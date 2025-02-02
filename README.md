net: the clojure netty companion
================================

Net provides a clojure foundation to implement asynchronous networking
based on netty.

It is much narrower in scope and features than
[aleph](https://github.com/ztellman/aleph), which you might want to
look into if you want a full-fledged asynchronous programming toolkit
for clojure.

**net** is rather geared towards people with prior netty knowledge
wanting to keep the same workflow in, *hopefully*, idiomatic Clojure,
and nothing but standard clojure facilities.

- Light facades around netty concepts such as channels, pipelines,
  channel initializers and bootstraps
- Facilities to create TLS client and server contexts from PEM files
- Ring-like HTTP(S) server facade
- HTTP(S) client
- Simple interface to create TCP server with optional TLS support
- Clojure [core.async](https://github.com/clojure/core.async) support

## Documentation

Net now has full [API Documentation](http://pyr.github.io/net) and
[Guides](http://pyr.github.io/net/intro.html).

## Installation

Note that this repo is a hack for projects that wish to use
Clojure 1.8: all the spec stuff has been removed.

```clojure
[systems.billo/net "net-0.3.3-beta12-Clojure1.8"]
```

## Changelog

### 0.3.3-beta9

- Depend on netty 4.1.8-Final

### 0.3.3-beta8

- specs for http server options

### 0.3.3-beta7

- Small fixes

### 0.3.3-beta6

- Allow user-supplied executor for responses (thanks @mpenet).
- Fix HTTP-related regressions introduced by reflection work.

### 0.3.3-beta4

- Ensure all calls do not need reflection.
- Correctly terminate clients in tcp server shutdown fn.

### 0.3.3-beta3

- Break `HandlerAdapter` into several protocols

### 0.3.3-beta2

- Add documentation and guides
- Improved specs

### 0.3.3-beta1

- Rework HTTP support to be aligned with
  [jet](https://github.com/mpenet/jet)
- Provide a single HTTP server interface, which allows aggregating or
  streaming body content.

### 0.2.20

- Allow user-supplied max body size

### 0.2.19

- Bugfix release for 0.2.18
- More restrictive specs

### 0.2.18

- Convenience macros to create encoders and decoders.

### 0.2.17

- `core.spec` schemas instead of prismatic schema
- Rely on Netty 4.1.6
- Additional sugar for futures and channels

## Thanks

- CRHough (https://github.com/CRHough) for a number of small fixes.
- Max Penet (https://github.com/mpenet) for most of the reflection fixes.

## License

Copyright © 2015, 2016, 2017 Pierre-Yves Ritschard, MIT License.
