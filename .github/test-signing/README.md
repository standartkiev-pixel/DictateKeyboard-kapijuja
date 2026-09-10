# Public prerelease signing identity

`kapijuja-prerelease-debug.keystore.b64` is an intentionally public, test-only Android debug key.
It exists because clean GitHub runners otherwise create a different debug certificate for every build,
which makes Android reject the next prerelease with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

This key signs only the `.debug` application ID. It is not confidential, grants no repository or service
access, and must never sign the stable `net.kapijuja.dictate` application. The production Kapijuja JKS
remains external to Git and will be connected through `keystore.properties` when it is recovered.

Decoded keystore SHA-256: `100a4ef0a4d5d2c702e5b9828f6fd71acb051fd61fc85c98ba14379cfd4700d2`

Certificate SHA-256: `22b02aed48a0ca0aeaa96e276e78ddefa000478edd824c8cb89da6575f068884`
