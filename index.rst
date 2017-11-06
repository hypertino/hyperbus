Hyperbus
========

If we take some software system, apply SRP and DRY principles and split it to the services, that can be deployed independently, what we'll get in the end is actually are microservices. The term isn't strictly defined and we refer to those microservices just as a services. Anyway those services should become small enough to be able to be reused and loosely coupled to be maintainable.

To make those services loosely cooupled there are numerous techinques such as:

- using messaging instead of RPC;
- prefering one-way communication over two-way AKA request/reply;
- transport and protocol abstraction;
- using some glue platforms with rules like ESB;
- using common conventions, protocols, RFCs like HTTP/REST.

What Hypertino is trying to provide is a framework and components to implement services with balanced set of these principles.

Hypertino services communicate over the Hyperbus.

> Before we go into the details it's worth to mention that protocol and message formats utilized in Hyperbus are language and platform agnostic. We don't use things like Java serialization or Java/Scala specific types to be able to communicate with software written in other languages. Hopefully, one day there will support for other languages.

Hyperbus provides an abstraction and interface for communication that is more or less transport independent. In this aspect it's like ESB, however it's not a real ESB with all the middleware. Instead it have locally (inproc) defined configuration on how services are mapped to the specific transport.

Other important thing is that while being transport agnostic Hyperbus is REST oriented and it maps to the HTTP quite transparently. This makes services more loosely coupled (from the implementation of each other) at the same time making them more coupled with the conventions of REST.

Hyperbus utilizes URL's that resembles WEB urls: `hb://service-name/path`, where `hb://` could map to any supported transport. `service-name` is used to resolve that service (Hyperbuse have pluggable resolvers, like `consul-resolver`). And `/path` is service specific path of the resource. It's also mandatory to specify the method when we do something with the resource, just like in the HTTP: `GET`, `POST`, `PATCH` or `DELETE`. Responses have status code which is directly maps to HTTP response status code.

Hyperbus provides just two patterns of communication:

1. `ask` two-way that has request and response;
2. `publish` which is one-way.

TODO: example of two services communicating with each other for each pattern.
TODO: Scala specific details

RAML
----

TODO: raml service specification

Protocol
--------

Transports
----------

- Inproc
- ZeroMQ
- Kafka

- Akka.Cluster (outdated)

Resolvers/registrators
----------------------

- Dummy
- Consul
