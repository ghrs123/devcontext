import { API_BASE_URL, WIDGET_VERSION } from './config.js';
import { getRecommendation } from './api.js';
import widgetStyles from './styles.css?inline';
import {
  renderTrigger,
  renderForm,
  renderResult,
  renderError
} from './ui.js';

const CONTAINER_SELECTOR = '[data-fitvision-product-id]';
const STYLE_TAG_ID = 'fitvision-widget-styles';

function ensureStylesInjected() {
  if (typeof document === 'undefined') {
    return;
  }

  if (document.getElementById(STYLE_TAG_ID)) {
    return;
  }

  const styleTag = document.createElement('style');
  styleTag.id = STYLE_TAG_ID;
  styleTag.textContent = widgetStyles;
  document.head.appendChild(styleTag);
}

function normalizeLocale(locale) {
  return locale === 'pt' ? 'pt' : 'en';
}

function readConfig(container) {
  const productId = (container.dataset.fitvisionProductId || '').trim();
  const apiKey = (container.dataset.fitvisionKey || '').trim();
  const apiBaseUrl = (container.dataset.fitvisionApiUrl || API_BASE_URL).trim();
  const locale = normalizeLocale((container.dataset.fitvisionLocale || 'en').trim());

  if (!productId || !apiKey) {
    console.warn('[FitVision] Missing required data attributes: data-fitvision-product-id and data-fitvision-key.', container);
    return null;
  }

  container.dataset.fitvisionLocale = locale;
  return { productId, apiKey, apiBaseUrl, locale };
}

function attachTryAgain(container, onRestart) {
  const retry = container.querySelector('.fitvision-try-again');
  if (!retry) {
    return;
  }
  retry.addEventListener('click', (event) => {
    event.preventDefault();
    onRestart();
  });
}

function mountFlow(container, config) {
  const showTrigger = () => {
    const triggerButton = renderTrigger(container);
    triggerButton.addEventListener('click', (event) => {
      event.preventDefault();
      showForm();
    });
  };

  const showForm = () => {
    renderForm(container, async (formData) => {
      try {
        const payload = {
          externalProductId: config.productId,
          heightCm: formData.heightCm,
          weightKg: formData.weightKg,
          gender: formData.gender || 'UNISEX',
          age: formData.age,
          storeBodyData: false
        };

        const recommendation = await getRecommendation(
          config.apiKey,
          payload,
          config.apiBaseUrl
        );

        renderResult(container, recommendation);
        attachTryAgain(container, showForm);
      } catch (error) {
        renderError(container, error);
        attachTryAgain(container, showForm);
      }
    });

    const backLink = container.querySelector('.fitvision-back-link');
    if (backLink) {
      backLink.addEventListener('click', (event) => {
        event.preventDefault();
        showTrigger();
      });
    }
  };

  showTrigger();
}

function init(root = document) {
  try {
    ensureStylesInjected();

    const containers = root.querySelectorAll(CONTAINER_SELECTOR);
    containers.forEach((container) => {
      if (container.dataset.fitvisionInitialized === 'true') {
        return;
      }

      const config = readConfig(container);
      if (!config) {
        return;
      }

      container.dataset.fitvisionInitialized = 'true';
      mountFlow(container, config);
    });
  } catch (error) {
    console.error('[FitVision] Initialization failed.', error);
  }
}

if (globalThis.window !== undefined) {
  globalThis.FitVision = globalThis.FitVision || {};
  globalThis.FitVision.init = init;
  globalThis.FitVision.version = WIDGET_VERSION;
}

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => init());
  } else {
    init();
  }
}
