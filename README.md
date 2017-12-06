# cmr.dev.env.manager

[![Build Status][travis-badge]][travis]
[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]
[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo-large]

*An Alternate Development Environment Manager for the CMR*


## Setup

The the documentation [instructions for setup][setup].


## Usage

With the setup done, you are ready to run `lein repl`. Having done that, you
will see output similar to the following:

```
Development Environment Manager
.....................................................................................
::'######::'##::::'##:'########::::::::'##:::::::::::::::::::::::::::::::::::::::::::
:'##... ##: ###::'###: ##.... ##::::::'##:::dMMMMb::::::::dMMMMMP::::::dMMMMMMMMb::::
: ##:::..:: ####'####: ##:::: ##:::::'##:::dMP.VMP:::::::dMP....::::::dMP"dMP"dMP::::
: ##::::::: ## ### ##: ########:::::'##:::dMP:dMP.::::::dMMMP::::::::dMP.dMP.dMP.::::
: ##::::::: ##. #: ##: ##.. ##:::::'##:::dMP:aMP.:amr::dMP..:::amr::dMP:dMP:dMP.:amr:
: ##::: ##: ##:.:: ##: ##::. ##:::'##:::dMMMMP.::dMP::dMMMMMP:dMP::dMP:dMP:dMP.:dMP::
:. ######:: ##:::: ##: ##:::. ##:'##::::......:::..:::......::..:::..::..::..:::..:::
::......:::..:::::..::..:::::..::..::::::::::::::::::::::::::::::::::::::::::::::::::

for NASA's Earthdata Common Metadata Repository

Loading ...
```

After a few seconds, the REPL will be loaded and ready to use:

```
nREPL server started on port 54636 on host 127.0.0.1 - nrepl://127.0.0.1:54636
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.8.0
Java HotSpot(TM) 64-Bit Server VM 1.8.0_60-b27
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

[cmr.dev.env.manager.repl] λ=>
```

To bring up a dev system, complete with all enabled running services:

```clj
[cmr.dev.env.manager.repl] λ=> (run)
```

Note that each service is started in its own OS process. For more information
on this, see the architecture section below.

## Documentation

[Guides and API docs are here][docs].

## Architecture Overview

[View docs][arch-overview].


## Background

For information on what problem this project attempts to define and how it was
originally planned, see the [background info][background-info].


## License

Apache License, Version 2.0.


<!-- Named page links below: /-->

[travis]: https://travis-ci.org/cmr-exchange/dev-env-manager
[travis-badge]: https://travis-ci.org/cmr-exchange/dev-env-manager.png?branch=master
[logo]: resources/images/cmr-dev-env-mgr.png
[logo-large]: resources/images/cmr-dev-env-mgr-large.png
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/dev-env-manager.svg
[tag]: https://github.com/cmr-exchange/dev-env-manager/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.8.0-blue.svg
[jdk-v]: https://img.shields.io/badge/jdk-1.7+-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-dev-env-manager
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-dev-env-manager.svg
[docs]: https://cmr-exchange.github.io/dev-env-manager/
[arch-overview]: https://cmr-exchange.github.io/dev-env-manager/current/1000-architecture.html
[background-info]: https://cmr-exchange.github.io/dev-env-manager/current/0000-background.html
[setup]: https://cmr-exchange.github.io/dev-env-manager/current/2000-setup.html
