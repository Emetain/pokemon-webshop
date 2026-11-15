# The web image for the server: Caddy with the built frontend (build context: the repository root, after
# "npm run build"). It also carries compose.yml, which deploy.sh reads from it: that way every version brings its own
# compose file, and the deploy key on the server can't bring one of its own.
FROM caddy:2.11-alpine
LABEL org.opencontainers.image.source=https://github.com/Emetain/pokemon-webshop
LABEL org.opencontainers.image.description="Poketopia frontend and reverse proxy"

COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY deploy/compose.yml /etc/poketopia/compose.yml
COPY frontend/dist /srv

# Stops the build on a broken Caddyfile, before it can reach the server: once for the closed preview and once for
# the open shop (SHOP_ACCESS), with an alias as www.<domain> would be. The values are only for this check.
RUN for access in preview open; do \
      SHOP_ACCESS=$access SITE_ADDRESS=example.com SITE_ALIASES=www.example.com PREVIEW_USER=check \
      PREVIEW_PASSWORD_HASH='$2a$14$dmnHmVdY8GjMSfIFQUmY9e7l.9jLN0bG34G5Q6/ugKLWjmSR.qtNa' \
      caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile || exit 1; \
    done
