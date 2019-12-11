# scarif

[![Build Status](https://travis-ci.org/csm/scarif.svg?branch=master)](https://travis-ci.org/csm/scarif)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.csm/scarif.svg)](https://clojars.org/com.github.csm/scarif)
[![cljdoc badge](https://cljdoc.org/badge/com.github.csm/scarif)](https://cljdoc.org/d/com.github.csm/scarif/CURRENT)

A configuration library for Clojure building on [Archaius](https://github.com/Netflix/archaius).

## Usage

```clojure
[com.github.csm/scarif "0.1.9"]
```

[API Documentation](https://csm.github.io/scarif/)

```clojure
(ns my-ns
  (:require [clojure.spec.alpha :as s]
            [scarif.core :as sc]
            'scarif.dynamodb))

; Install dynamodb configuration source.
(scarif.dynamodb/init!)

; Use my-config-var as a regular var.
; The value will come from the DynamicStringProperty my-ns/my-config-var, parsed
; as EDN.
(sc/defconfig my-config-var :foo)

; for dynamic config sources, you can install a callback that is invoked if the
; value changes
(sc/defconfig ^{:on-change (fn [old new] ,,,)} my-config-2)

; If a spec is defined for your var, values coming from Archaius will be validated
; against it before the var is changed.
(s/def :my-ns/my-config-3 keyword?)
(sc/defconfig my-config-3)

; You can access the property itself via the :property metadata
(-> #'my-ns/my-config-3 meta :property (.getValue))
```

## License

Copyright © 2018, 2019 Casey Marshall

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
