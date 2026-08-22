# CLI Notes

The CLI entry point is `org.vstu.meaningtree.Main` in `modules/application`. Build the shaded CLI jar with:

```shell
mvn -pl modules/application -am package
```

Run it with:

```shell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar <command> [options]
```

Use the CLI for quick conversion/serialization checks when that is faster than writing a full test, but still add or update `.test` cases for durable conversion behavior.
