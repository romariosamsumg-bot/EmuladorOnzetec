// Teste de ponta a ponta da página de envio (roda no Chromium como se fosse um celular).
// Uso: node phone_transfer.js <url_base> <url_qr> <saida_screenshots> <arquivo1> [arquivo2...]
const { chromium } = require("playwright");

(async () => {
  const [base, qr, outDir, ...files] = process.argv.slice(2);
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: "pt-BR" });
  const page = await ctx.newPage();
  const fail = async (msg) => { console.error("FALHOU: " + msg); await page.screenshot({ path: outDir + "/web_erro.png" }); process.exit(1); };
  try {
    // 1) Sem PIN na URL: pede o PIN; PIN errado mostra a mensagem.
    await page.goto(base);
    await page.fill("#pin", "0000");
    await page.click("#pin-btn");
    await page.waitForFunction(() => document.querySelector("#pin-msg").textContent.includes("PIN incorreto"), null, { timeout: 5000 });
    await page.screenshot({ path: outDir + "/web_1_pin_errado.png" });

    // 2) URL do QR code (PIN embutido): entra direto na tela de envio.
    await page.goto(qr);
    await page.waitForSelector("#send-card:not(.hidden)", { timeout: 5000 });
    const options = await page.$$eval("#system option", (o) => o.map((x) => x.textContent));
    if (!options.includes("Super Nintendo") || !options.includes("Neo Geo")) await fail("consoles ausentes: " + options);
    await page.screenshot({ path: outDir + "/web_2_pronto.png" });

    // 3) Envia os arquivos e espera todos "Enviado".
    await page.setInputFiles("#files", files);
    await page.waitForFunction((n) => document.querySelectorAll("#list .status.ok").length === n, files.length, { timeout: 20000 });
    const statuses = await page.$$eval("#list .status", (s) => s.map((x) => x.textContent));
    console.log("STATUS: " + JSON.stringify(statuses));
    await page.screenshot({ path: outDir + "/web_3_enviados.png" });
    await browser.close();
    process.exit(0);
  } catch (e) {
    await fail(e.message);
  }
})();
