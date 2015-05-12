(ns me.moocar.websocket.client-dev
  (:require [clojure.core.async :as async :refer [<!!]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.client :as websocket-client]))

(defonce client nil)

(defn new-client []
  (let [config (read-string (slurp "config.edn"))
        log-ch (async/chan 100)
        transport-chans {:send-ch (async/chan 1)
                         :recv-ch (async/chan 1)}]
    (assoc (websocket-client/construct config)
           :transport-chans transport-chans
           :log-ch log-ch)))

(defn go []
  (alter-var-root #'client (constantly (new-client)))
  (alter-var-root #'client #(when % (component/start %)))
  :ready)

(defn reset []
  (alter-var-root #'client #(when % (component/stop %)))
  (refresh :after 'me.moocar.websocket.client-dev/go))

(defn pr-log []
  (async/go-loop []
    (when-let [msg (async/<! (:log-ch client))]
      (pprint msg)
      (recur))))

(defn pr-session []
  (pprint (.getCookieStore (:jetty-client client))))
