(ns me.moocar.client.system
  (:require []))

(defn new-system
  [config transport-chans]
  (component/system-map
   :client (client/new-pub-client config client-transport-chans)
   ))
