# FitVision — Site de Documentação (HTML estático)

Visualizador profissional dos ficheiros Markdown em [`../docs/`](../docs/). **Não duplica conteúdo** — carrega os `.md` em tempo real via `fetch`.

**Última sincronização de conteúdo:** junho de 2026

## Abrir localmente

### Opção recomendada — servidor HTTP

A partir da **raiz do repositório**:

```bash
cd c:\workspace\devcontext
npm run docs
```

Abra no browser: **http://localhost:4000/docs-site/**

Alternativa sem npm script:

```bash
cd c:\workspace\devcontext
npx serve . -p 4000
```

Ou a partir desta pasta (`docs-site/`):

```bash
cd c:\workspace\devcontext\docs-site
npm run docs
```

(mesmo resultado — serve a raiz do repo na porta 4000)

### Limitação: `file://`

Abrir `index.html` diretamente no browser (**duplo-clique**) **não funciona**: o browser bloqueia `fetch()` para ficheiros locais (CORS / política de ficheiros). Use sempre um servidor estático.

## Estrutura

```
docs-site/
├── index.html      # Shell (sidebar + área de conteúdo)
├── css/style.css   # Layout responsivo, tema claro/escuro
├── js/app.js       # Routing, fetch MD, Mermaid, Prism, TOC
├── package.json    # Script npm run docs
└── README.md       # Este ficheiro
```

Os Markdown permanecem em `docs/` (22 ficheiros: README + 01–20 + AUDIT).

## Funcionalidades

| Funcionalidade | Detalhe |
|----------------|---------|
| Navegação | Sidebar com os 22 documentos, agrupados por secção |
| Pesquisa | Filtro na sidebar por título/ficheiro |
| Links internos | `[texto](./06-api-reference.md)` abre o doc correto no viewer |
| Mermaid | Diagramas em blocos ` ```mermaid ` renderizados via CDN |
| Syntax highlight | Prism.js (Java, JS, TS, Bash, JSON, YAML, SQL, markup) |
| TOC | Índice lateral “Nesta página” (h2/h3) em ecrãs largos |
| Tema | Toggle claro/escuro (persiste em `localStorage`) |
| Mobile | Menu hamburger + sidebar deslizante |

## Routing

URLs por hash:

- Índice: `#/README`
- Doc específico: `#/04-fluxos-da-aplicacao`
- Com âncora: `#/06-api-reference#autenticacao`

## Dependências (CDN, sem build)

- [marked.js](https://marked.js.org/) — Markdown → HTML
- [Mermaid](https://mermaid.js.org/) — diagramas
- [Prism.js](https://prismjs.com/) — highlight de código

Requer ligação à internet na **primeira** visita para carregar os CDN; depois o browser pode cachear.

## Manutenção

Ao adicionar um novo `.md` em `docs/`:

1. Registe-o no array `DOCS` em `js/app.js`
2. Atualize `docs/README.md` se for parte do índice oficial

Não é necessário rebuild — basta recarregar a página.
