(ns me.moocar.ftb500.server.routes.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.server.routes.add-game :as add-game]))

(defn new-system
  []
  (component/system-map
   :game/add (add-game/new-add-game)))
