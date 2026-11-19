# Assignment 3 subtitle API

This project contains the Spring Boot subtitle server and the separate
`VideoPlayerMultiThreaded` JavaFX application. The player uploads the selected
media to this server asynchronously, renders returned timestamped subtitle
segments on frames, and provides an on/off control and `s` shortcut.

## Run

Set `OPENAI_API_KEY` or place the key by itself in `key.txt` in the server's
working directory, then run `./gradlew bootRun`. The server listens on
`localhost:8080`. `OPENAI_API_KEY_FILE` can be used to select a different key
file. In another terminal, run the player from its directory with Java 25:

```sh
mvn compile javafx:run -Djavafx.args="--audio --subtitle-frames movie.mp4"
```

The server must be running before requesting subtitles. The player submits
files up to 25 MB; larger files are reported as unavailable rather than
blocking playback.

## API contract

Send a `multipart/form-data` `POST` to
`http://localhost:8080/api/v1/subtitles` with the original media audio in a
`file` part (the server also accepts an `audio` part). The server forwards it
to OpenAI's audio-transcriptions endpoint using `whisper-1`, `verbose_json`,
and both word and segment timestamp granularities.

The JSON response contains `text`, `language`, `duration` (seconds), and
timestamped `segments` and `words`. A segment is omitted when both
`no_speech_prob > 0.6` and `avg_logprob < -1.0`; its words are omitted too.
The player can use segment times to schedule subtitle phrases and word times
for finer synchronization. Supported uploads are limited to 25 MB, matching
the transcription API's file-size limit.

`GET http://localhost:8080/api/v1/global/usage` returns
`{"usage": <seconds>}` with the cumulative duration successfully transcribed
since this server process started. This counter is concurrency-safe and is
not persisted across restarts.
