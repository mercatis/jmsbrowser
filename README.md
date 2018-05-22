JMS Browser
===========

Send, view and browse your messages in queues and topics easily from a powerful Eclipse based user interface.

Features
--------
* runs on linux and windows
* multiple, simultaneous connections and views
* copy messages from one server to another
* filter messages with selectors
* XML formatting of payload
* live view
* supports JMS standard 1.1
* plugin mechanism for JMS providers
* uses browse for queues

Build
-----
In order to build JMS Browser, it is necessary to add the libraries listed in each plugins lib/libraries.txt to the plugins lib folder.
Afterwards, you can start the build with

    bash$ mvn package

This places the builds for windows and linux under `jmsbrowser.product/target/products/`.

Contributing
------------
You are welcome to fork and add the features most valuable to you. Please consider discussing new features by opening an issue before submitting a merge request.

