const http = require('node:http');
const handler = require('./lib/http');
const port = Number(process.env.PORT || 8080);
http.createServer(handler).listen(port, () => console.log(`OrderApi escuchando en ${port}`));
