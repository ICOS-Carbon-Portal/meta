# Multi-stage build, both stages on Ubuntu 24.04.
#
# Ubuntu 24.04 ships Erlang/OTP 25 via apt, which is fine, but its `elixir`
# package is far too old (1.14) for this project (needs ~> 1.18 and the
# built-in JSON module). So we take Erlang from apt and drop in a pinned,
# precompiled Elixir matching the installed OTP major.
#
# The builder assembles a self-contained mix release (ERTS bundled), so the
# runtime stage needs no Erlang/Elixir at all — just a few shared libraries.

# ---- builder ---------------------------------------------------------------
FROM ubuntu:24.04 AS builder

# Bump to build against a different Elixir; must be a 1.18+ release that ships
# a precompiled zip for the OTP major installed below.
ARG ELIXIR_VERSION=1.18.4

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    MIX_ENV=prod

RUN apt-get update && apt-get install -y --no-install-recommends \
        erlang-nox \
        erlang-dev \
        build-essential \
        git \
        curl \
        unzip \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Elixir precompiled for whatever OTP major apt just gave us.
RUN set -eux; \
    otp_major="$(erl -noshell -eval 'io:format("~s", [erlang:system_info(otp_release)]), halt().')"; \
    curl -fsSL -o /tmp/elixir.zip \
        "https://github.com/elixir-lang/elixir/releases/download/v${ELIXIR_VERSION}/elixir-otp-${otp_major}.zip"; \
    unzip -q /tmp/elixir.zip -d /opt/elixir; \
    rm /tmp/elixir.zip
ENV PATH="/opt/elixir/bin:${PATH}"

RUN mix local.hex --force && mix local.rebar --force

WORKDIR /app

# Fetch and compile dependencies first so they cache independently of source.
COPY mix.exs mix.lock ./
RUN mix deps.get --only prod && mix deps.compile

# Build the release.
COPY config config
COPY lib lib
RUN mix compile && mix release --overwrite

# ---- runtime ---------------------------------------------------------------
FROM ubuntu:24.04 AS runtime

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

# ERTS is bundled in the release; we only need the libraries its NIFs link
# against (OpenSSL for crypto/TLS, ncurses for the runtime) plus CA certs for
# outbound HTTPS to DataCite.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openssl \
        libncurses6 \
        libstdc++6 \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user.
RUN useradd --create-home --uid 10001 app
USER app
WORKDIR /home/app

COPY --from=builder --chown=app:app /app/_build/prod/rel/biblio_materializer ./

# Boots in the foreground and runs population passes continuously. Configure via
# the env vars in the README, e.g.
#   docker run --rm -e VIRTUOSO_HOST=http://virtuoso:8890 <image>
CMD ["bin/biblio_materializer", "start"]
