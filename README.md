Usage
====
```
mvn clean compile assembly:single
java -Dambient.datadog.key=<KEY> -Dambient.hostname=<HOSTNAME> -jar target/ambient-1.0-SNAPSHOT-jar-with-dependencies.jar &
```