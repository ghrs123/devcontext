const TEXTS = {
  en: {
    trigger: 'Find my size',
    formTitle: 'Tell us your measurements',
    height: 'Height (cm)',
    weight: 'Weight (kg)',
    gender: 'Gender',
    age: 'Age (optional)',
    genderMale: 'Male',
    genderFemale: 'Female',
    genderUnisex: 'Prefer not to say',
    submit: 'Get my size',
    back: 'Back',
    loading: 'Finding your size...',
    resultTitle: 'Your recommended size',
    confidence: 'Confidence',
    tryAgain: 'Try again',
    regionLabel: 'Size recommendation',
    noChartMessage: "We don't have size data for this product yet. Please consult the brand's size guide.",
    noMatchMessage: 'We found the closest match, but we recommend checking the size guide before ordering.',
    networkError: 'Could not connect. Please try again.',
    required: 'Height and weight are required.',
    invalidHeight: 'Height must be between 50 and 250 cm.',
    invalidWeight: 'Weight must be between 20 and 300 kg.',
    invalidAge: 'Age must be between 10 and 120 if provided.'
  },
  pt: {
    trigger: 'Encontrar o meu tamanho',
    formTitle: 'Indique as suas medidas',
    height: 'Altura (cm)',
    weight: 'Peso (kg)',
    gender: 'Género',
    age: 'Idade (opcional)',
    genderMale: 'Masculino',
    genderFemale: 'Feminino',
    genderUnisex: 'Prefiro não dizer',
    submit: 'Obter o meu tamanho',
    back: 'Voltar',
    loading: 'A encontrar o seu tamanho...',
    resultTitle: 'Tamanho recomendado',
    confidence: 'Confiança',
    tryAgain: 'Tentar novamente',
    regionLabel: 'Recomendação de tamanho',
    noChartMessage: 'Ainda não temos dados de tamanhos para este produto. Consulte o guia de tamanhos da marca.',
    noMatchMessage: 'Encontrámos a correspondência mais próxima, mas recomendamos confirmar no guia de tamanhos antes de comprar.',
    networkError: 'Não foi possível ligar. Tente novamente.',
    required: 'Altura e peso são obrigatórios.',
    invalidHeight: 'A altura deve estar entre 50 e 250 cm.',
    invalidWeight: 'O peso deve estar entre 20 e 300 kg.',
    invalidAge: 'A idade deve estar entre 10 e 120, se preenchida.'
  }
};

function getLocale(container) {
  return container.dataset.fitvisionLocale === 'pt' ? 'pt' : 'en';
}

function t(container) {
  return TEXTS[getLocale(container)];
}

function clearContainer(container) {
  while (container.firstChild) {
    container.firstChild.remove();
  }
  container.classList.add('fitvision-container');
}

function createText(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  node.textContent = text;
  return node;
}

function buildStableId(container, suffix) {
  const base = container.dataset.fitvisionProductId || 'product';
  const clean = base.replaceAll(/[^a-zA-Z0-9_-]/g, '-').slice(0, 32);
  return `fitvision-${clean}-${suffix}`;
}

function createField(labelText, input, inputId) {
  const field = document.createElement('div');
  field.className = 'fitvision-field';

  const label = document.createElement('label');
  label.className = 'fitvision-label';
  label.htmlFor = inputId;
  label.textContent = labelText;

  input.id = inputId;
  input.classList.add('fitvision-input');

  field.appendChild(label);
  field.appendChild(input);
  return field;
}

export function renderTrigger(container) {
  const copy = t(container);
  clearContainer(container);

  const wrapper = document.createElement('div');
  wrapper.className = 'fitvision-trigger';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'fitvision-trigger-button';
  button.textContent = copy.trigger;

  wrapper.appendChild(button);
  container.appendChild(wrapper);
  return button;
}

export function renderForm(container, onSubmit) {
  const copy = t(container);
  clearContainer(container);

  const wrapper = document.createElement('div');
  wrapper.className = 'fitvision-form-wrapper';

  const title = createText('h3', 'fitvision-form-title', copy.formTitle);

  const form = document.createElement('form');
  form.className = 'fitvision-form';

  const errorId = buildStableId(container, 'error');
  form.setAttribute('aria-describedby', errorId);

  const error = createText('p', 'fitvision-form-error', '');
  error.id = errorId;

  const heightInput = document.createElement('input');
  heightInput.type = 'number';
  heightInput.min = '50';
  heightInput.max = '250';
  heightInput.step = '0.1';
  heightInput.required = true;

  const weightInput = document.createElement('input');
  weightInput.type = 'number';
  weightInput.min = '20';
  weightInput.max = '300';
  weightInput.step = '0.1';
  weightInput.required = true;

  const genderSelect = document.createElement('select');
  [
    ['MALE', copy.genderMale],
    ['FEMALE', copy.genderFemale],
    ['UNISEX', copy.genderUnisex]
  ].forEach(([value, label]) => {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = label;
    genderSelect.appendChild(option);
  });

  const ageInput = document.createElement('input');
  ageInput.type = 'number';
  ageInput.min = '10';
  ageInput.max = '120';
  ageInput.step = '1';

  form.appendChild(createField(copy.height, heightInput, buildStableId(container, 'height')));
  form.appendChild(createField(copy.weight, weightInput, buildStableId(container, 'weight')));
  form.appendChild(createField(copy.gender, genderSelect, buildStableId(container, 'gender')));
  form.appendChild(createField(copy.age, ageInput, buildStableId(container, 'age')));

  const actions = document.createElement('div');
  actions.className = 'fitvision-form-actions';

  const submit = document.createElement('button');
  submit.type = 'submit';
  submit.className = 'fitvision-submit-button';
  submit.textContent = copy.submit;

  const back = document.createElement('a');
  back.href = '#';
  back.className = 'fitvision-back-link';
  back.textContent = copy.back;

  actions.appendChild(submit);
  actions.appendChild(back);

  form.appendChild(error);
  form.appendChild(actions);

  form.addEventListener('submit', (event) => {
    event.preventDefault();

    const heightCm = Number(heightInput.value);
    const weightKg = Number(weightInput.value);
    const age = ageInput.value ? Number(ageInput.value) : null;

    if (!heightInput.value || !weightInput.value) {
      error.textContent = copy.required;
      return;
    }
    if (Number.isNaN(heightCm) || heightCm < 50 || heightCm > 250) {
      error.textContent = copy.invalidHeight;
      return;
    }
    if (Number.isNaN(weightKg) || weightKg < 20 || weightKg > 300) {
      error.textContent = copy.invalidWeight;
      return;
    }
    if (age !== null && (Number.isNaN(age) || age < 10 || age > 120)) {
      error.textContent = copy.invalidAge;
      return;
    }

    error.textContent = '';
    renderLoading(container);

    try {
      Promise.resolve(onSubmit({
        heightCm,
        weightKg,
        gender: genderSelect.value || 'UNISEX',
        age
      })).catch(() => {});
    } catch {
      // Intentionally swallowed to prevent bubbling into host page error tracking.
    }
  });

  wrapper.appendChild(title);
  wrapper.appendChild(form);
  container.appendChild(wrapper);
}

export function renderLoading(container) {
  const copy = t(container);
  clearContainer(container);

  const loading = document.createElement('div');
  loading.className = 'fitvision-loading';
  loading.setAttribute('role', 'status');
  loading.setAttribute('aria-live', 'polite');

  const spinner = document.createElement('span');
  spinner.className = 'fitvision-loading-spinner';
  spinner.setAttribute('aria-hidden', 'true');

  const text = createText('span', 'fitvision-loading-text', copy.loading);

  loading.appendChild(spinner);
  loading.appendChild(text);
  container.appendChild(loading);
}

export function renderResult(container, data) {
  const copy = t(container);
  clearContainer(container);

  const card = document.createElement('div');
  card.className = 'fitvision-result';
  card.setAttribute('role', 'region');
  card.setAttribute('aria-label', copy.regionLabel);

  card.appendChild(createText('h3', 'fitvision-result-title', copy.resultTitle));

  if (!data || data.hasSizeChart === false) {
    card.appendChild(createText('p', 'fitvision-result-message', copy.noChartMessage));
  } else if (data.quality === 'NO_MATCH') {
    card.appendChild(createText('p', 'fitvision-result-message', copy.noMatchMessage));
    if (data.message) {
      card.appendChild(createText('p', 'fitvision-result-api-message', data.message));
    }
  } else {
    const size = createText('p', 'fitvision-result-size', data.recommendedSize || '-');
    card.appendChild(size);

    const badge = createText('p', 'fitvision-result-confidence', `${copy.confidence}: ${data.confidenceLabel || 'N/A'}`);
    card.appendChild(badge);

    if (data.message) {
      card.appendChild(createText('p', 'fitvision-result-message', data.message));
    }
  }

  const retry = document.createElement('a');
  retry.href = '#';
  retry.className = 'fitvision-try-again';
  retry.textContent = copy.tryAgain;
  card.appendChild(retry);

  container.appendChild(card);
}

export function renderError(container, error) {
  const copy = t(container);
  clearContainer(container);

  const card = document.createElement('div');
  card.className = 'fitvision-error';
  card.setAttribute('role', 'alert');

  const isNetworkError = error?.name === 'NetworkError';
  let message = copy.networkError;
  if (!isNetworkError && error?.message) {
    message = error.message;
  }

  card.appendChild(createText('p', 'fitvision-error-message', message));

  const retry = document.createElement('a');
  retry.href = '#';
  retry.className = 'fitvision-try-again';
  retry.textContent = copy.tryAgain;
  card.appendChild(retry);

  container.appendChild(card);
}
