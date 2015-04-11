(ns me.moocar.client.system
  (:require []))

(defn new-system
  [config]
  (let [client-transport-chans {:send-ch (async/chan 1)
                                :recv-ch (async/chan 1)}]
   (component/system-map
    ::me.moocar.ftb500.client/transport-chans client-transport-chans
    :client (client/new-pub-client client-transport-chans)
    )))
