// Onzetec Games — interações do site (sem dependências).
// Sons: os mesmos do app (desligados até o visitante ligar no botão ♪).

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const base = new URL("..", import.meta.url); // pasta site/assets/

// ------------------------------------------------------------ sons
const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* sem armazenamento */ } },
};
let soundOn = store.get("og-sound") === "1";
const cache = new Map();
function sfx(name, volume = 0.5) {
  if (!soundOn) return;
  let a = cache.get(name);
  if (!a) { a = new Audio(new URL(`audio/${name}.ogg`, base)); cache.set(name, a); }
  const s = a.cloneNode();
  s.volume = volume;
  s.play().catch(() => {});
}
const music = new Audio(new URL("audio/music.ogg", base));
music.loop = true;
music.volume = 0.25;

function renderSound() {
  const btn = $(".sound-toggle");
  if (!btn) return;
  btn.setAttribute("aria-pressed", String(soundOn));
  btn.textContent = soundOn ? "♪" : "🔇";
  btn.title = soundOn ? "Desligar som e música" : "Ligar som e música";
}
function setSound(on) {
  soundOn = on;
  store.set("og-sound", on ? "1" : "0");
  renderSound();
  if (on) { music.play().catch(() => {}); sfx("success", 0.4); } else music.pause();
}
$(".sound-toggle")?.addEventListener("click", () => setSound(!soundOn));
renderSound();
if (soundOn) {
  // O navegador só toca depois de um gesto: a música volta no primeiro clique ou tecla.
  const resume = () => music.play().catch(() => {});
  addEventListener("pointerdown", resume, { once: true });
  addEventListener("keydown", resume, { once: true });
}

// "Tic" ao passar o mouse; "select" ao clicar em botões e links.
let lastMove = 0;
document.addEventListener("pointerover", (e) => {
  const t = e.target.closest(".btn, .chip, .card, .cart, .consoles figure, .game, nav a");
  if (!t || t.contains(e.relatedTarget)) return;
  const now = performance.now();
  if (now - lastMove > 60) { lastMove = now; sfx("move", 0.25); }
});
document.addEventListener("click", (e) => {
  const t = e.target.closest("a, button");
  if (!t) return;
  sfx(t.matches("[download], .download") ? "launch" : "select", 0.4);
  // Vibração leve no celular (onde o navegador permitir).
  navigator.vibrate?.(12);
});

// ------------------------------------------------------------ menu atual
const here = location.pathname.replace(/index\.html$/, "");
$$(".topbar nav a").forEach((a) => {
  const p = new URL(a.href).pathname.replace(/index\.html$/, "");
  if (p === here) a.setAttribute("aria-current", "page");
});

// ------------------------------------------------------------ TV da página inicial
const screen = $(".tv .screen");
if (screen) {
  const imgs = $$("img", screen);
  const label = $(".tv .label");
  let i = 0;
  const show = () => {
    imgs.forEach((im, k) => im.classList.toggle("on", k === i));
    if (label) label.textContent = imgs[i].alt;
    i = (i + 1) % imgs.length;
  };
  show();
  if (!matchMedia("(prefers-reduced-motion: reduce)").matches) setInterval(show, 2600);
}

// ------------------------------------------------------------ recomendação pelo aparelho
const ua = navigator.userAgent;
const isTv = /Android TV|AFT|BRAVIA|GoogleTV|SmartTV|Tizen|Web0S|TV;/i.test(ua);
const isPhone = !isTv && /Android/i.test(ua) && /Mobile/i.test(ua);
const isAndroidTablet = !isTv && /Android/i.test(ua) && !/Mobile/i.test(ua);
const rec = isTv ? ".cart.tv" : isPhone || isAndroidTablet ? ".cart.phone" : null;
if (rec) $(rec)?.classList.add("recommended");

// ------------------------------------------------------------ copiar endereço
$$("[data-copy]").forEach((b) => {
  b.addEventListener("click", async () => {
    const text = $(b.dataset.copy)?.textContent?.trim();
    if (!text) return;
    try { await navigator.clipboard.writeText(text); b.textContent = "Copiado!"; } catch { b.textContent = "Selecione e copie"; }
    setTimeout(() => (b.textContent = "Copiar"), 1800);
  });
});
// Endereços curtos para digitar no app Downloader (com o caminho real do site).
$$("[data-short]").forEach((el) => {
  el.textContent = new URL(el.dataset.short, location.href.replace(/[^/]*$/, "")).href.replace(/^https?:\/\//, "");
});

// ------------------------------------------------------------ manual: sumário que acompanha a leitura
const toc = $$(".manual aside a[href^='#']");
if (toc.length) {
  const byId = new Map(toc.map((a) => [a.hash.slice(1), a]));
  const obs = new IntersectionObserver((entries) => {
    entries.forEach((en) => {
      if (!en.isIntersecting) return;
      toc.forEach((a) => a.classList.remove("active"));
      const a = byId.get(en.target.id);
      a?.classList.add("active");
      a?.scrollIntoView({ block: "nearest", inline: "nearest" });
    });
  }, { rootMargin: "-90px 0px -70% 0px" });
  $$(".manual article [id]").forEach((h) => obs.observe(h));
}

// ------------------------------------------------------------ código Konami: ↑↑↓↓←→←→ B A
const code = ["ArrowUp", "ArrowUp", "ArrowDown", "ArrowDown", "ArrowLeft", "ArrowRight", "ArrowLeft", "ArrowRight", "b", "a"];
let pos = 0;
addEventListener("keydown", (e) => {
  pos = e.key.toLowerCase() === code[pos].toLowerCase() || e.key === code[pos] ? pos + 1 : (e.key === code[0] ? 1 : 0);
  if (pos < code.length) return;
  pos = 0;
  const burst = document.createElement("div");
  burst.className = "konami-burst";
  burst.textContent = "+30 VIDAS!";
  document.body.append(burst);
  setTimeout(() => burst.remove(), 2300);
  const colors = ["#ff7a1a", "#ffb547", "#3fd0e0", "#8b6cff", "#5bd68a", "#ff7a6b"];
  for (let k = 0; k < 60; k++) {
    const p = document.createElement("i");
    p.className = "pixel-confetti";
    p.style.left = `${Math.random() * 100}vw`;
    p.style.top = `${-10 - Math.random() * 30}px`;
    p.style.background = colors[k % colors.length];
    p.style.setProperty("--dx", `${(Math.random() - 0.5) * 200}px`);
    p.style.setProperty("--rot", `${Math.random() * 720}deg`);
    p.style.animationDelay = `${Math.random() * 0.5}s`;
    document.body.append(p);
    setTimeout(() => p.remove(), 2600);
  }
  const was = soundOn; soundOn = true; sfx("success", 0.6); soundOn = was;
});
