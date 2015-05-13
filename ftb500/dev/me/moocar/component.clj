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

(defmacro with-test-systems [[binding-form n system-map] & body]
  `(let [systems# (repeatedly ~n #(component/start ~system-map))
         ~binding-form systems#]
     (try ~@body
          (finally
            (try
              (doseq [system# systems#]
                (component/stop system#))
                (catch Throwable t#
                  (.printStackTrace t#)))))))
