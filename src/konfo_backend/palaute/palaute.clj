(ns konfo-backend.palaute.palaute
  (:require
    [konfo-backend.elastic-tools :refer [search]]))

(defn post-palaute
  [arvosana palaute]
    (konfo-backend.elastic-tools/insert "palaute"  {:arvosana arvosana :palaute palaute}))

(defn- create-palaute
  [hit]
  (let [palaute (:_source hit)]
    {:arvosana (:arvosana palaute)
     :palaute (:palaute palaute)
     :created (:created palaute)}))

(defn- create-palautteet
  [result]
  (let [palautteet (map create-palaute (:hits result))]
    {:count (:total result)
     :avg (if (> (:total result) 0)
            (/ (reduce + (map #(:arvosana %) palautteet)) (:total result))
            0)
     :result palautteet}))

(defn get-palautteet
  [after]
  (search "palaute"
          create-palautteet
          :query {:bool {:must {:range {:created {:gt after}}}}}
          :_source ["arvosana" "palaute" "created"]
          :sort [{:created :desc}]))