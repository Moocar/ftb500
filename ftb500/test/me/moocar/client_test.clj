(ns me.moocar.websocket-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >!]]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.websocket.full-system :as full-system]))

(deftest t-all
  (let [config {:port 8080
                :hostname "localhost"}
        system (component/start (full-system/new-system config))]
    (try
      (let [{:keys [client]} system]
        (println "response" (<!! (client/add-game client))))
      (finally
        (component/stop system)))))
