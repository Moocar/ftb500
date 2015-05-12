(ns me.moocar.websocket.server-dev
  (:require [clojure.core.async :as async :refer [<!! go-loop >! <!]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.server :as websocket-server]))

(defonce server nil)

(defn new-server []
  (let [config (read-string (slurp "config.edn"))
        log-ch (async/chan 100)
        recv-ch (async/chan 100)]
    (go-loop []
      (when-let [msg (<! recv-ch)]
        (>! log-ch msg)
        (recur)))
    (assoc (websocket-server/construct config)
           :log-ch log-ch
           :recv-ch recv-ch)))

(defn go []
  (alter-var-root #'server (constantly (new-server)))
  (alter-var-root #'server #(when % (component/start %)))
  :ready)

(defn reset []
  (alter-var-root #'server #(when % (component/stop %)))
  (refresh :after 'me.moocar.websocket.server-dev/go))

(defn pr-log []
  (go-loop []
    (when-let [msg (<! (:log-ch server))]
      (pprint msg)
      (recur))))
