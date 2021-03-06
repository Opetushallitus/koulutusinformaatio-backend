(ns konfo-backend.search.koulutus.search
  (:require
    [konfo-backend.tools :refer [log-pretty]]
    [konfo-backend.search.tools :refer :all]
    [konfo-backend.search.query :refer [query match-all-query hakutulos-aggregations jarjestajat-aggregations inner-hits-query sorts external-query]]
    [konfo-backend.search.response :refer [parse parse-inner-hits-for-jarjestajat parse-external]]
    [konfo-backend.elastic-tools :as e]
    [konfo-backend.search.koulutus.kuvaukset :refer [with-kuvaukset]]))

(defonce index "koulutus-kouta-search")

(def koulutus-kouta-search (partial e/search-with-pagination index))

(defn search
  [keyword lng page size sort order constraints]
  (let [query (if (match-all? keyword constraints)
                (match-all-query)
                (query keyword constraints))
        aggs (hakutulos-aggregations constraints)]
    (log-pretty query)
    (log-pretty aggs)
    (koulutus-kouta-search
      page
      size
      #(-> % parse with-kuvaukset)
      :_source ["oid", "nimi", "koulutukset", "tutkintonimikkeet", "kielivalinta", "kuvaus", "teemakuva", "eperuste", "opintojenLaajuus", "opintojenLaajuusyksikko", "opintojenLaajuusNumero", "koulutustyyppi", "tutkinnonOsat", "osaamisala"]
      :sort (sorts sort order lng)
      :query query
      :aggs aggs)))

(defn search-koulutuksen-jarjestajat
  [oid lng page size order tuleva? constraints]
  (let [query (inner-hits-query oid lng page size order tuleva? constraints)
        aggs (jarjestajat-aggregations tuleva? constraints)]
    (e/search index
              parse-inner-hits-for-jarjestajat
              :_source ["oid", "koulutukset", "nimi"]
              :query query
              :aggs aggs)))

(defn external-search
  [keyword lng page size sort order constraints]
  (let [query (external-query keyword lng constraints)]
    (log-pretty query)
    (koulutus-kouta-search
      page
      size
      #(-> % parse-external with-kuvaukset)
      :_source ["oid", "nimi", "koulutukset", "tutkintonimikkeet", "kielivalinta", "kuvaus", "teemakuva", "eperuste", "opintojenLaajuus", "opintojenLaajuusyksikko", "opintojenLaajuusNumero", "koulutustyyppi"]
      :sort (sorts sort order lng)
      :query query)))
