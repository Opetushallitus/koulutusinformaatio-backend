(ns konfo-backend.search.filters
  (:require
    [konfo-backend.koodisto.koodisto :as k]
    [konfo-backend.tools :refer [reduce-merge-map]]))

(defn- koodi->filter
  [aggs koodi]
  (let [koodiUri  (keyword (:koodiUri koodi))
        nimi      (get-in koodi [:nimi])
        count     (koodiUri aggs)
        alakoodit (when (contains? koodi :alakoodit)
                    (reduce-merge-map #(koodi->filter aggs %) (:alakoodit koodi)))]

    {koodiUri (cond->     {:nimi nimi}
                count     (assoc :count count)
                alakoodit (assoc :alakoodit alakoodit))}))

(defn- koodisto->filters
  [aggs koodisto]
  (reduce-merge-map #(koodi->filter aggs %) (:koodit (k/get-koodisto koodisto))))

;TODO! Koodisto
(defn- beta-koulutustyyppi
  [aggs]
  (let [count (:amm aggs)]
    {:amm (cond-> {:nimi {:fi "Ammatillinen koulutus"}
                   :alakoodit (select-keys (koodisto->filters aggs "koulutustyyppi") [:koulutustyyppi_1 :koulutustyyppi_11 :koulutustyyppi_12])}
            count (assoc :count count))}))

(defn hierarkia
  ([aggs]
   (let [filters (partial koodisto->filters aggs)]
     {:opetuskieli    (filters "oppilaitoksenopetuskieli")
      :maakunta       (filters "maakunta")
      :kunta          (filters "kunta")
      :koulutustyyppi (beta-koulutustyyppi aggs) ;TODO! Koodisto
      :koulutusala    (filters "kansallinenkoulutusluokitus2016koulutusalataso1")}))
  ([]
   (hierarkia {})))