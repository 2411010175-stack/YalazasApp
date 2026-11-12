import test from 'node:test';
import assert from 'node:assert/strict';
import { miniaturaCloudinary } from '../src/catalogo.ts';

test('Cloudinary sirve una miniatura y conserva el identificador de imagen', () => {
  assert.equal(
    miniaturaCloudinary('https://res.cloudinary.com/yalazas/image/upload/v123/alitas.jpg', 400, 240),
    'https://res.cloudinary.com/yalazas/image/upload/c_fill,w_400,h_240,q_auto,f_auto/v123/alitas.jpg',
  );
});

test('una URL externa queda intacta', () => {
  assert.equal(miniaturaCloudinary('https://ejemplo.com/alitas.jpg', 400, 240),
    'https://ejemplo.com/alitas.jpg');
});
