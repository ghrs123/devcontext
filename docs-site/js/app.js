/**
 * FitVision Documentation Site
 * Loads markdown from /docs/ (repo root) — requires HTTP server.
 */

(function () {
  'use strict';

  /** @type {{ id: string, file: string, title: string, group?: string }[]} */
  const DOCS = [
    { id: 'README', file: 'README.md', title: 'Índice', group: 'Início' },
    { id: '01-visao-geral', file: '01-visao-geral.md', title: '01 — Visão Geral', group: 'Fundamentos' },
    { id: '02-documentacao-funcional', file: '02-documentacao-funcional.md', title: '02 — Documentação Funcional', group: 'Fundamentos' },
    { id: '03-arquitetura-tecnica', file: '03-arquitetura-tecnica.md', title: '03 — Arquitetura Técnica', group: 'Fundamentos' },
    { id: '04-fluxos-da-aplicacao', file: '04-fluxos-da-aplicacao.md', title: '04 — Fluxos da Aplicação', group: 'Fundamentos' },
    { id: '05-modelo-de-dados', file: '05-modelo-de-dados.md', title: '05 — Modelo de Dados', group: 'Fundamentos' },
    { id: '06-api-reference', file: '06-api-reference.md', title: '06 — Referência da API', group: 'Componentes' },
    { id: '07-widget-integration', file: '07-widget-integration.md', title: '07 — Integração do Widget', group: 'Componentes' },
    { id: '08-dashboard-store', file: '08-dashboard-store.md', title: '08 — Dashboard (Loja)', group: 'Componentes' },
    { id: '09-admin-area', file: '09-admin-area.md', title: '09 — Área Admin', group: 'Componentes' },
    { id: '10-shopify-app', file: '10-shopify-app.md', title: '10 — Shopify App', group: 'Componentes' },
    { id: '11-seguranca-e-multitenancy', file: '11-seguranca-e-multitenancy.md', title: '11 — Segurança e Multitenancy', group: 'Operações' },
    { id: '12-gdpr-e-privacidade', file: '12-gdpr-e-privacidade.md', title: '12 — GDPR e Privacidade', group: 'Operações' },
    { id: '13-execucao-local', file: '13-execucao-local.md', title: '13 — Execução Local', group: 'Operações' },
    { id: '14-configuracao-env', file: '14-configuracao-env.md', title: '14 — Configuração e Env', group: 'Operações' },
    { id: '15-testes', file: '15-testes.md', title: '15 — Testes', group: 'Operações' },
    { id: '16-deploy', file: '16-deploy.md', title: '16 — Deploy', group: 'Operações' },
    { id: '17-observabilidade-operacoes', file: '17-observabilidade-operacoes.md', title: '17 — Observabilidade', group: 'Operações' },
    { id: '18-roadmap-pendencias', file: '18-roadmap-pendencias.md', title: '18 — Roadmap e Pendências', group: 'Referência' },
    { id: '19-guia-para-novo-developer', file: '19-guia-para-novo-developer.md', title: '19 — Guia Novo Developer', group: 'Referência' },
    { id: '20-glossario', file: '20-glossario.md', title: '20 — Glossário', group: 'Referência' },
    { id: 'AUDIT', file: 'AUDIT.md', title: 'AUDIT — Auditoria Técnica', group: 'Referência' },
  ];

  const DOCS_BASE = '/docs/';
  const DEFAULT_DOC = 'README';

  const fileToId = Object.fromEntries(DOCS.map((d) => [d.file, d.id]));
  const idToDoc = Object.fromEntries(DOCS.map((d) => [d.id, d]));

  /** @type {string | null} */
  let currentDocId = null;

  const els = {
    sidebarNav: document.getElementById('sidebar-nav'),
    navSearch: document.getElementById('nav-search'),
    docContent: document.getElementById('doc-content'),
    loading: document.getElementById('loading'),
    errorPanel: document.getElementById('error-panel'),
    errorMessage: document.getElementById('error-message'),
    topbarTitle: document.getElementById('topbar-title'),
    toc: document.getElementById('toc'),
    tocNav: document.getElementById('toc-nav'),
    sidebar: document.getElementById('sidebar'),
    sidebarOverlay: document.getElementById('sidebar-overlay'),
    menuToggle: document.getElementById('menu-toggle'),
    sidebarClose: document.getElementById('sidebar-close'),
    themeToggle: document.getElementById('theme-toggle'),
  };

  // ── Theme ──────────────────────────────────────────────────────────────

  function initTheme() {
    const stored = localStorage.getItem('fv-docs-theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    applyTheme(stored || (prefersDark ? 'dark' : 'light'));
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('fv-docs-theme', theme);
    if (typeof mermaid !== 'undefined') {
      mermaid.initialize(getMermaidConfig(theme));
    }
  }

  function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    applyTheme(current === 'dark' ? 'light' : 'dark');
    if (currentDocId) {
      renderMermaid();
    }
  }

  function getMermaidConfig(theme) {
    const isDark = theme === 'dark';
    return {
      startOnLoad: false,
      theme: isDark ? 'dark' : 'default',
      securityLevel: 'loose',
      fontFamily: '"Segoe UI", system-ui, sans-serif',
    };
  }

  // ── Marked setup ───────────────────────────────────────────────────────

  function escapeHtml(text) {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function slugify(text) {
    return text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');
  }

  function resolveMdHref(href) {
    if (!href) return null;
    if (href.startsWith('http://') || href.startsWith('https://') || href.startsWith('#')) {
      return null;
    }
    const clean = href.split('#')[0].replace(/^\.\//, '');
    if (!clean.endsWith('.md')) return null;
    return clean;
  }

  function configureMarked() {
    // marked@12 UMD still invokes custom renderers with the legacy (text, level) API
    // when passed via marked.use({ renderer }). Token-object signatures + this.parser
    // only apply to the built-in Renderer class, not merged override objects.
    const renderer = {
      heading(text, depth) {
        const plain = text.replace(/<[^>]+>/g, '');
        const id = slugify(plain);
        return `<h${depth} id="${id}">${text}</h${depth}>\n`;
      },

      code(code, lang, escaped) {
        const language = (lang || '').trim().toLowerCase();
        const text = escaped ? code : escapeHtml(code);
        if (language === 'mermaid') {
          return `<div class="mermaid">${text}</div>\n`;
        }
        const langClass = language ? `language-${language}` : 'language-plaintext';
        return `<pre class="line-numbers"><code class="${langClass}">${text}</code></pre>\n`;
      },

      link(href, title, text) {
        const mdFile = resolveMdHref(href);
        if (mdFile && fileToId[mdFile]) {
          const docId = fileToId[mdFile];
          const hash = href.includes('#') ? href.split('#').slice(1).join('#') : '';
          const dataHash = hash ? ` data-hash="${escapeHtml(hash)}"` : '';
          const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
          return `<a href="#/${docId}" class="internal-doc-link" data-doc="${docId}"${dataHash}${titleAttr}>${text}</a>`;
        }
        const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
        const external = href && (href.startsWith('http://') || href.startsWith('https://'));
        const target = external ? ' target="_blank" rel="noopener noreferrer"' : '';
        return `<a href="${escapeHtml(href || '#')}"${titleAttr}${target}>${text}</a>`;
      },
    };

    marked.use({
      gfm: true,
      breaks: false,
      renderer,
    });
  }

  // ── Navigation ─────────────────────────────────────────────────────────

  function buildSidebar() {
    const groups = {};
    for (const doc of DOCS) {
      const group = doc.group || 'Outros';
      if (!groups[group]) groups[group] = [];
      groups[group].push(doc);
    }

    els.sidebarNav.innerHTML = '';
    for (const [groupName, docs] of Object.entries(groups)) {
      const label = document.createElement('p');
      label.className = 'nav-group-label';
      label.textContent = groupName;
      els.sidebarNav.appendChild(label);

      for (const doc of docs) {
        const link = document.createElement('a');
        link.href = `#/${doc.id}`;
        link.className = 'nav-link';
        link.dataset.docId = doc.id;
        link.dataset.search = `${doc.title} ${doc.file}`.toLowerCase();
        link.textContent = doc.title;
        els.sidebarNav.appendChild(link);
      }
    }
  }

  function setActiveNav(docId) {
    els.sidebarNav.querySelectorAll('.nav-link').forEach((link) => {
      link.classList.toggle('active', link.dataset.docId === docId);
    });
  }

  function filterNav(query) {
    const q = query.trim().toLowerCase();
    els.sidebarNav.querySelectorAll('.nav-link').forEach((link) => {
      const match = !q || link.dataset.search.includes(q);
      link.classList.toggle('hidden-by-search', !match);
    });
    els.sidebarNav.querySelectorAll('.nav-group-label').forEach((label) => {
      let sibling = label.nextElementSibling;
      let hasVisible = false;
      while (sibling && !sibling.classList.contains('nav-group-label')) {
        if (sibling.classList.contains('nav-link') && !sibling.classList.contains('hidden-by-search')) {
          hasVisible = true;
          break;
        }
        sibling = sibling.nextElementSibling;
      }
      label.style.display = hasVisible || !q ? '' : 'none';
    });
  }

  // ── TOC ────────────────────────────────────────────────────────────────

  function buildToc() {
    const headings = els.docContent.querySelectorAll('h2, h3');
    els.tocNav.innerHTML = '';

    if (headings.length === 0) {
      els.toc.hidden = true;
      return;
    }

    els.toc.hidden = false;
    headings.forEach((heading) => {
      if (!heading.id) {
        heading.id = slugify(heading.textContent || '');
      }
      const link = document.createElement('a');
      link.href = `#${heading.id}`;
      link.className = `toc-link level-${heading.tagName === 'H3' ? '3' : '2'}`;
      link.textContent = heading.textContent;
      link.addEventListener('click', (e) => {
        e.preventDefault();
        heading.scrollIntoView({ behavior: 'smooth', block: 'start' });
        history.replaceState(null, '', `#/${currentDocId}#${heading.id}`);
      });
      els.tocNav.appendChild(link);
    });
  }

  // ── Load & render ──────────────────────────────────────────────────────

  async function loadDoc(docId, scrollTarget) {
    const doc = idToDoc[docId];
    if (!doc) {
      await loadDoc(DEFAULT_DOC);
      return;
    }

    currentDocId = docId;
    setActiveNav(docId);
    els.topbarTitle.textContent = doc.title;
    document.title = `${doc.title} — FitVision Docs`;

    els.loading.hidden = false;
    els.errorPanel.hidden = true;
    els.docContent.innerHTML = '';

    try {
      const url = `${DOCS_BASE}${doc.file}`;
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status} ao carregar ${url}`);
      }
      const markdown = await response.text();
      els.docContent.innerHTML = marked.parse(markdown);
      els.loading.hidden = true;

      highlightCode();
      await renderMermaid();
      buildToc();
      bindInternalLinks();
      closeMobileSidebar();

      if (scrollTarget) {
        requestAnimationFrame(() => {
          const target = document.getElementById(scrollTarget);
          if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        });
      } else {
        window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'auto' });
      }
    } catch (err) {
      els.loading.hidden = true;
      els.errorPanel.hidden = false;
      els.errorMessage.textContent = err instanceof Error ? err.message : String(err);

      if (window.location.protocol === 'file:') {
        els.errorMessage.textContent +=
          ' — Abrir via file:// bloqueia fetch. Use um servidor HTTP local (ver instruções abaixo).';
      }
    }
  }

  function highlightCode() {
    els.docContent.querySelectorAll('pre code').forEach((block) => {
      if (block.closest('.mermaid')) return;
      Prism.highlightElement(block);
    });
  }

  async function renderMermaid() {
    const nodes = els.docContent.querySelectorAll('.mermaid:not([data-processed])');
    if (nodes.length === 0) return;

    const theme = document.documentElement.getAttribute('data-theme') || 'light';
    mermaid.initialize(getMermaidConfig(theme));

    let index = 0;
    for (const node of nodes) {
      const source = node.textContent || '';
      const id = `mermaid-${currentDocId}-${index++}`;
      try {
        const { svg } = await mermaid.render(id, source);
        node.innerHTML = svg;
        node.setAttribute('data-processed', 'true');
      } catch (err) {
        console.warn('Mermaid render error:', err);
        node.innerHTML = `<pre class="mermaid-error">${escapeHtml(source)}</pre>`;
        node.setAttribute('data-processed', 'error');
      }
    }
  }

  function bindInternalLinks() {
    els.docContent.querySelectorAll('a.internal-doc-link').forEach((link) => {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        const docId = link.dataset.doc;
        const hash = link.dataset.hash || '';
        navigateTo(docId, hash);
      });
    });
  }

  // ── Routing ────────────────────────────────────────────────────────────

  function parseRoute() {
    const hash = window.location.hash.replace(/^#\/?/, '');
    if (!hash) return { docId: DEFAULT_DOC, anchor: '' };

    const parts = hash.split('#');
    const docPart = parts[0] || DEFAULT_DOC;
    const anchor = parts.slice(1).join('#');

    if (idToDoc[docPart]) {
      return { docId: docPart, anchor };
    }

    // Legacy: direct filename e.g. #06-api-reference.md
    const withMd = docPart.endsWith('.md') ? docPart : `${docPart}.md`;
    if (fileToId[withMd]) {
      return { docId: fileToId[withMd], anchor };
    }

    return { docId: DEFAULT_DOC, anchor: '' };
  }

  function navigateTo(docId, anchor) {
    const hash = anchor ? `#/${docId}#${anchor}` : `#/${docId}`;
    if (window.location.hash !== hash) {
      window.location.hash = hash;
    } else {
      loadDoc(docId, anchor || undefined);
    }
  }

  function onHashChange() {
    const { docId, anchor } = parseRoute();
    loadDoc(docId, anchor || undefined);
  }

  // ── Mobile sidebar ─────────────────────────────────────────────────────

  function openMobileSidebar() {
    els.sidebar.classList.add('open');
    els.sidebarOverlay.hidden = false;
    els.sidebarOverlay.classList.add('visible');
    els.menuToggle.setAttribute('aria-expanded', 'true');
  }

  function closeMobileSidebar() {
    els.sidebar.classList.remove('open');
    els.sidebarOverlay.classList.remove('visible');
    els.sidebarOverlay.hidden = true;
    els.menuToggle.setAttribute('aria-expanded', 'false');
  }

  // ── Init ───────────────────────────────────────────────────────────────

  function init() {
    initTheme();
    configureMarked();
    buildSidebar();

    els.navSearch.addEventListener('input', (e) => {
      filterNav(e.target.value);
    });

    els.themeToggle.addEventListener('click', toggleTheme);

    els.menuToggle.addEventListener('click', openMobileSidebar);
    els.sidebarClose.addEventListener('click', closeMobileSidebar);
    els.sidebarOverlay.addEventListener('click', closeMobileSidebar);

    document.querySelector('.brand').addEventListener('click', (e) => {
      e.preventDefault();
      navigateTo(DEFAULT_DOC);
    });

    window.addEventListener('hashchange', onHashChange);

    if (!window.location.hash) {
      window.location.hash = `#/${DEFAULT_DOC}`;
    } else {
      onHashChange();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
