# Stage 1: Build stage
FROM node:20-alpine AS build
WORKDIR /app

# Copy package.json and install dependencies
COPY package.json ./
RUN npm install

# Copy source files and build
COPY . .
RUN npm run build

# Stage 2: Production runtime stage
FROM nginx:alpine

# Copy built files from build stage to Nginx web root
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration for API routing
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
