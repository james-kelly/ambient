import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.HttpTransport;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

public class Ambient {

    private final MetricRegistry registry = new MetricRegistry();
    private final Histogram ampHistogram = registry.histogram(name(Ambient.class, "amp"));
    private final Histogram peakHistogram = registry.histogram(name(Ambient.class, "peak"));


    private void update(float amp, float peak) {
        ampHistogram.update((int) (amp * 100));
        peakHistogram.update((int) (peak * 100));
    }

    public Ambient() throws IOException {
        String apiKey = System.getProperty("ambient.datadog.key");
        String hostname = System.getProperty("ambient.hostname");
        System.out.println("ambient.datadog.key   = " + apiKey);
        System.out.println("ambient.hostname = " + hostname);

        HttpTransport httpTransport = new HttpTransport.Builder().withApiKey(apiKey).build();
        DatadogReporter reporter = DatadogReporter
                .forRegistry(registry)
                .withEC2Host()
                .withTransport(httpTransport)
                .withTags(Collections.singletonList("hostname:" + hostname))
                .build();
        reporter.start(10, TimeUnit.SECONDS);

    }

    public static void main(String[] args) throws IOException {
        Ambient meter = new Ambient();
        new Thread(new Recorder(meter)).start();
    }

    /**
     * http://stackoverflow.com/questions/26574326/how-to-calculate-the-level-amplitude-db-of-audio-signal-in-java
     */
    static class Recorder implements Runnable {
        final Ambient meter;

        Recorder(final Ambient meter) {
            this.meter = meter;
        }

        @Override
        public void run() {
            AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
            final int bufferByteSize = 2048;

            TargetDataLine line;
            try {
                line = AudioSystem.getTargetDataLine(fmt);
                line.open(fmt, bufferByteSize);
            } catch (LineUnavailableException e) {
                System.err.println(e);
                return;
            }

            byte[] buf = new byte[bufferByteSize];
            float[] samples = new float[bufferByteSize / 2];

            float lastPeak = 0f;

            line.start();
            for (int b; (b = line.read(buf, 0, buf.length)) > -1; ) {

                // convert bytes to samples here
                for (int i = 0, s = 0; i < b; ) {
                    int sample = 0;

                    sample |= buf[i++] & 0xFF; // (reverse these two lines
                    sample |= buf[i++] << 8;   //  if the format is big endian)

                    // normalize to range of +/-1.0f
                    samples[s++] = sample / 32768f;
                }

                float rms = 0f;
                float peak = 0f;
                for (float sample : samples) {

                    float abs = Math.abs(sample);
                    if (abs > peak) {
                        peak = abs;
                    }

                    rms += sample * sample;
                }

                rms = (float) Math.sqrt(rms / samples.length);

                if (lastPeak > peak) {
                    peak = lastPeak * 0.875f;
                }

                lastPeak = peak;

                meter.update(rms, peak);
            }
        }
    }
}