(ns me.moocar.component
  (:require [com.stuartsierra.component :as component]))

(defmacro with-test-system [[binding-form system-map] & body]
  `(let [system# (component/start ~system-map)
         ~binding-form system#]
     (try ~@body
          (finally
            (try (component/stop system#)
                 (catch Throwable t#
                   (.printStackTrace t#)))))))
