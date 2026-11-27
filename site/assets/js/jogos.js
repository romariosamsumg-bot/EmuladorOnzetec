// Página "Baixar jogos": lê data/jogos.json e monta os consoles (com o pacote de cada um),
// o pacote geral e a lista de jogos com busca e filtro.
const $ = (s, el = document) => el.querySelector(s);
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const https = (u) => (/^https:\/\//i.test(u ?? "") ? u : null);
// Só entra o que tem licença e página oficial do autor (garantia de que pode ser distribuído).
const valido = (i) => i && https(i.url) && i.licenca && https(i.fonte);

const data = await fetch("data/jogos.json", { cache: "no-cache" }).then((r) => r.json()).catch(() => ({ consoles: {}, jogos: [] }));
const consoles = Object.entries(data.consoles ?? {});
const jogos = (data.jogos ?? []).filter((j) => j.titulo && j.console && valido(j));
let filtro = "todos";
let busca = "";

function geral() {
  const p = data.pacoteGeral;
  $("#geral").innerHTML = valido(p)
    ? `<a class="btn download" href="${esc(p.url)}" download><span class="ico">📦</span><span>Baixar tudo${p.tamanho ? ` · ${esc(p.tamanho)}` : ""}<small>todos os consoles · ${esc(p.licenca)}</small></span></a>`
    : `<span class="btn ghost" aria-disabled="true" style="opacity:.6;cursor:default"><span class="ico">📦</span><span>Baixar tudo<small>em breve</small></span></span>`;
}

function cards() {
  $("#consoles").innerHTML = consoles.map(([id, c]) => {
    const n = jogos.filter((j) => j.console === id).length;
    const p = c.pacote;
    const botao = valido(p)
      ? `<a class="btn small download" href="${esc(p.url)}" download>⬇ Pacote${p.tamanho ? ` · ${esc(p.tamanho)}` : ""}</a>`
      : `<span class="btn small ghost" aria-disabled="true" style="opacity:.6;cursor:default">Em breve</span>`;
    return `<figure class="reveal" data-console="${esc(id)}">
      <img src="assets/img/consoles/${esc(id)}.webp" alt="" loading="lazy" width="660" height="440">
      <figcaption><span>${esc(c.nome)}</span><small>${n === 1 ? "1 jogo" : `${n} jogos`}</small></figcaption>
      <div style="padding:0 12px 12px;display:flex;gap:8px;flex-wrap:wrap">
        ${botao}
        ${n ? `<button class="btn small ghost" type="button" data-ver="${esc(id)}">Ver jogos</button>` : ""}
      </div>
    </figure>`;
  }).join("");
  $("#consoles").onclick = (e) => {
    const b = e.target.closest("[data-ver]");
    if (!b) return;
    filtro = b.dataset.ver;
    chips();
    lista();
    $("#lista-titulo").scrollIntoView({ behavior: "smooth" });
  };
}

function chips() {
  const usados = new Set(jogos.map((j) => j.console));
  const opcoes = [["todos", "Todos"], ...consoles.filter(([id]) => usados.has(id)).map(([id, c]) => [id, c.nome])];
  $("#filtros").innerHTML = opcoes.map(([id, nome]) => `<button class="chip" type="button" data-f="${esc(id)}" aria-pressed="${id === filtro}">${esc(nome)}</button>`).join("");
  $("#filtros").onclick = (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    filtro = b.dataset.f;
    chips();
    lista();
  };
}

function lista() {
  const el = $("#lista");
  if (jogos.length === 0) {
    el.innerHTML = `<div class="empty"><div class="big">🧩</div><p><strong>Os primeiros jogos estão chegando.</strong></p>
      <p>Enquanto isso, veja no <a href="manual.html#colocar-jogos">manual</a> como colocar os seus jogos no app.</p></div>`;
    return;
  }
  const itens = jogos.filter((j) => (filtro === "todos" || j.console === filtro) && (!busca || j.titulo.toLowerCase().includes(busca)));
  if (itens.length === 0) { el.innerHTML = `<div class="empty"><p>Nenhum jogo encontrado.</p></div>`; return; }
  el.innerHTML = `<div class="games">${itens.map((j) => `
    <article class="game">
      <img class="cover" src="${esc(https(j.capa) ?? `assets/img/consoles/${j.console}.webp`)}" alt="" loading="lazy">
      <div class="body">
        <h3>${esc(j.titulo)}</h3>
        <div class="meta">${esc(data.consoles?.[j.console]?.nome ?? j.console)}${j.ano ? " · " + esc(j.ano) : ""}${j.autor ? " · " + esc(j.autor) : ""}</div>
        <div class="meta">${esc(j.licenca)} · <a href="${esc(j.fonte)}" rel="noopener">site do autor</a>${j.tamanho ? " · " + esc(j.tamanho) : ""}</div>
        <a class="btn small download" href="${esc(j.url)}" download><span>⬇ Baixar</span></a>
      </div>
    </article>`).join("")}</div>`;
}

$("#busca")?.addEventListener("input", (e) => { busca = e.target.value.trim().toLowerCase(); lista(); });
geral();
cards();
chips();
lista();
