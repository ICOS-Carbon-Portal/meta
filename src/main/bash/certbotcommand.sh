#!/bin/bash

certbot certonly --non-interactive --webroot --webroot-path /usr/share/nginx/html/ --domain meta.fieldsites.se --email carbon.admin@nateko.lu.se --agree-tos
