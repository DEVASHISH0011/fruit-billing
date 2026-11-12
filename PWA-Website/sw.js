const CACHE='mansuri-billing-shell-v1';
const SHELL=['./','./index.html','./manifest.webmanifest','./icons/icon-192.png','./icons/icon-512.png'];
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)));self.skipWaiting();});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))));self.clients.claim();});
self.addEventListener('fetch',event=>{
  const req=event.request;
  if(req.method!=='GET')return;
  const url=new URL(req.url);
  if(url.origin===self.location.origin){
    event.respondWith(caches.match(req).then(cached=>cached||fetch(req).then(resp=>{
      if(resp.ok){const copy=resp.clone();caches.open(CACHE).then(c=>c.put(req,copy));}
      return resp;
    }).catch(()=>caches.match('./index.html'))));
  }
});
