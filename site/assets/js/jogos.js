// Página de jogos livres: lê data/jogos.json e monta filtros, pacotes e a grade.
const $ = (s, el = document) => el.querySelector(s);
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const safeUrl = (u) => (/^https:\/\//i.test(u ?? "") ? u : null);

const data = await fetch("data/jogos.json", { cache: "no-cache" }).then((r) => r.json()).catch(() => ({ consoles: {}, jogos: [] }));
// Só entram jogos com licença e página do autor (garantia de que podem ser redistribuídos).
const jogos = (data.jogos ?? []).filter((j) => j.titulo && j.licenca && safeUrl(j.fonte) && safeUrl(j.url));
let filtro = "todos";
let busca = "";

function chips() {
  const el = $("#filtros");
  const usados = new Set(jogos.map((j) => j.console));
  const opcoes = [["todos", "Todos"], ...Object.entries(data.consoles ?? {}).filter(([id]) => usados.has(id)).map(([id, c]) => [id, c.nome])];
  el.innerHTML = opcoes.map(([id, nome]) => `<button class="chip" type="button" data-f="${esc(id)}" aria-pressed="${id === filtro}">${esc(nome)}</button>`).join("");
  el.onclick = (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    filtro = b.dataset.f;
    chips();
    grade();
  };
}

function pacotes() {
  const el = $("#pacotes");
  const itens = [];
  if (safeUrl(data.pacoteGeral)) itens.push(`<a class="btn download" href="${esc(data.pacoteGeral)}" download><span class="ico">📦</span><span>Baixar tudo<small>todos os jogos livres</small></span></a>`);
  for (const [id, c] of Object.entries(data.consoles ?? {})) {
    if (safeUrl(c.pacote)) itens.push(`<a class="btn ghost download" href="${esc(c.pacote)}" download><span class="ico">🎮</span><span>${esc(c.nome)}<small>pacote do console</small></span></a>`);
  }
  el.innerHTML = itens.join("");
  el.hidden = itens.length === 0;
}

function grade() {
  const el = $("#lista");
  const lista = jogos.filter((j) => (filtro === "todos" || j.console === filtro) && (!busca || j.titulo.toLowerCase().includes(busca)));
  if (jogos.length === 0) {
    el.innerHTML = `<div class="empty"><div class="big">🧩</div><p><strong>Os primeiros jogos livres estão chegando.</strong></p>
      <p>Enquanto isso, veja no <a href="manual.html#colocar-jogos">manual</a> como colocar os seus jogos no app.</p></div>`;
    return;
  }
  if (lista.length === 0) { el.innerHTML = `<div class="empty"><p>Nenhum jogo encontrado.</p></div>`; return; }
  el.innerHTML = `<div class="games">${lista.map((j) => `
    <article class="game">
      ${safeUrl(j.capa) ? `<img class="cover" src="${esc(j.capa)}" alt="" loading="lazy">` : `<img class="cover" src="assets/img/consoles/${esc(j.console)}.webp" alt="" loading="lazy">`}
      <div class="body">
        <h3>${esc(j.titulo)}</h3>
        <div class="meta">${esc(data.consoles?.[j.console]?.nome ?? j.console)}${j.ano ? " · " + esc(j.ano) : ""}${j.autor ? " · " + esc(j.autor) : ""}</div>
        <div class="meta">Licença: ${esc(j.licenca)} · <a href="${esc(j.fonte)}" rel="noopener">site do autor</a>${j.tamanho ? " · " + esc(j.tamanho) : ""}</div>
        <a class="btn small download" href="${esc(j.url)}" download><span>⬇ Baixar</span></a>
      </div>
    </article>`).join("")}</div>`;
}

$("#busca")?.addEventListener("input", (e) => { busca = e.target.value.trim().toLowerCase(); grade(); });
chips();
pacotes();
grade();
