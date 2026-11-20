FROM node:22-slim

WORKDIR /app

COPY package.json ./
COPY server ./server

ENV NODE_ENV=production
ENV PORT=8080

EXPOSE 8080

CMD ["node", "server/sobdosuddhi-proxy.mjs"]
