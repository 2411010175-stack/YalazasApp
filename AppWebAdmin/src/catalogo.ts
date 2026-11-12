/** Cloudinary entrega un archivo acorde con la tarjeta; otras URL se conservan. */
export function miniaturaCloudinary(url: string, ancho: number, alto: number): string {
  if (!Number.isInteger(ancho) || !Number.isInteger(alto) || ancho < 1 || alto < 1 || ancho > 2000 || alto > 2000) return url;
  try {
    const imagen = new URL(url);
    if (imagen.protocol !== 'https:' || imagen.hostname !== 'res.cloudinary.com') return url;
    const prefijo = '/image/upload/';
    const posicion = imagen.pathname.indexOf(prefijo);
    if (posicion < 0 || !imagen.pathname.slice(posicion + prefijo.length)) return url;
    imagen.pathname = imagen.pathname.slice(0, posicion + prefijo.length) +
      `c_fill,w_${ancho},h_${alto},q_auto,f_auto/` + imagen.pathname.slice(posicion + prefijo.length);
    return imagen.toString();
  } catch { return url; }
}
