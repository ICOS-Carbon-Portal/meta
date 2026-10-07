defmodule LandingBench.Portal do
  @moduledoc """
  Fetches the data portal's main page (`/portal/`) and extracts the ENVRI
  configuration that the portal's JavaScript uses (`window.envri` and
  `window.envriConfig`), most importantly the meta host, which serves both
  the SPARQL endpoint and the data object landing pages.
  """

  defstruct [:portal_url, :envri, :meta_host, :data_item_prefix, :fetch_ms]

  @type t :: %__MODULE__{
          portal_url: String.t(),
          envri: String.t(),
          meta_host: String.t(),
          data_item_prefix: String.t(),
          fetch_ms: float()
        }

  @spec fetch(String.t(), keyword()) :: {:ok, t()} | {:error, term()}
  def fetch(data_base_url, req_opts) do
    url = String.trim_trailing(data_base_url, "/") <> "/portal/"

    with {:ok, resp, ms} <- LandingBench.Http.timed_get(url, req_opts),
         :ok <- expect_ok(resp, url),
         {:ok, envri} <- extract_envri(resp.body),
         {:ok, conf} <- extract_envri_config(resp.body) do
      {:ok,
       %__MODULE__{
         portal_url: url,
         envri: envri,
         meta_host: Map.fetch!(conf, "metaHost"),
         data_item_prefix: Map.fetch!(conf, "dataItemPrefix"),
         fetch_ms: ms
       }}
    end
  end

  defp expect_ok(%{status: 200}, _url), do: :ok
  defp expect_ok(%{status: status}, url), do: {:error, "GET #{url} returned HTTP #{status}"}

  defp extract_envri(html) do
    case Regex.run(~r/window\.envri\s*=\s*"([^"]+)"/, html) do
      [_, envri] -> {:ok, envri}
      nil -> {:error, "could not find window.envri in portal page"}
    end
  end

  defp extract_envri_config(html) do
    with [_, json] <- Regex.run(~r/window\.envriConfig\s*=\s*(\{.*?\});/s, html),
         {:ok, conf} <- JSON.decode(json) do
      {:ok, conf}
    else
      _ -> {:error, "could not parse window.envriConfig in portal page"}
    end
  end
end
