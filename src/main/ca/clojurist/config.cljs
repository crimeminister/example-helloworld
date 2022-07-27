(ns ca.clojurist.config
  "Load and extract data from the Solana CLI configuration file."
  {:author "Robert Medeiros" :email "robert@crimeminister.org"}
  (:require
   ["fs/promises" :as fs]
   ["os" :as os]
   ["path" :as path]
   ["yaml" :as yaml])
  (:require
   ["@solana/web3.js" :as web3])
  (:require
   [promesa.core :as p])
  (:require
   [ca.clojurist.keypair :as keypair]))

;; load
;; -----------------------------------------------------------------------------

(defn load&
  "Returns a promise that resolves to a map of parsed Solana CLI
  configuration."
  []
  (let [home (.homedir os)
        file-path (path/resolve home ".config" "solana" "cli" "config.yml")]
    (p/let [config-str (fs/readFile file-path #js{:encoding "utf8"})
            config (yaml/parse config-str)]
      (js->clj config))))

;; rpc-url
;; -----------------------------------------------------------------------------

(defn rpc-url
  "Extract the RPC URL from configuration, or fall back to localhost if
  missing."
  [config]
  (let [localhost "http://127.0.0.1:8899"]
    (when-not (contains? config "json_rpc_url")
      (js/console.warn "Missing RPC URL in CLI config, falling back to localhost"))
    (get config "json_rpc_url" localhost)))

;; payer
;; -----------------------------------------------------------------------------

(defn payer&
  "Return a promise that resolves to the payer defined in the Solana CLI
  config file."
  [config]
  (when-not (contains? config "keypair_path")
    (throw (ex-info "Missing keypair path" {:config config})))
  (let [keypair-path (get config "keypair_path")]
    (try
      (keypair/from-file& keypair-path)
      (catch js/Error e
        (js/console.warn "Failed to create keypair from CLI config file, making new random keypair")
        (keypair/random&)))))
