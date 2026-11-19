# COMP3011 Assignment 3 - Video Player

Start the Spring Boot server in the repository root before enabling subtitles.
See the root `README.md` for API configuration and the combined run command.

The `-S` / `--subtitle-frames` option enables the subtitle frame processor on
startup; subtitles can also be toggled during playback with the adjacent `CC`
button or the `s` key. The player sends media to
`http://localhost:8080/api/v1/subtitles` asynchronously and needs the server to
be running for transcription. Other existing playback options and frame
processors are unchanged.

Build and run with Java 25:

```sh
mvn compile javafx:run -Djavafx.args="--audio --subtitle-frames movie.mp4"
```
