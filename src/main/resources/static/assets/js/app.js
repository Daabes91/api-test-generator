    // Theme persistence (light by default)
    (function(){
      try{
        const saved = localStorage.getItem('theme-pref');
        if (saved === 'light' || saved === 'dark') document.documentElement.setAttribute('data-theme', saved);
        else document.documentElement.setAttribute('data-theme','light');
      }catch(e){}
    })();
    function updateToggle(){
      const cur = document.documentElement.getAttribute('data-theme');
      const t = document.getElementById('themeToggle');
      if (!t) return;
      t.setAttribute('data-state', cur === 'dark' ? 'dark' : 'light');
    }
    function toggleTheme(){
      const html = document.documentElement;
      const cur = html.getAttribute('data-theme') || 'light';
      const next = cur === 'dark' ? 'light' : 'dark';
      html.setAttribute('data-theme', next);
      try{ localStorage.setItem('theme-pref', next); }catch(e){}
      updateToggle();
    }

    const MESSAGE_TIMEOUT_MS = 6000;
    let messageRegionBound = false;

    function getMessageRegion(){
      const region = document.getElementById('appMessages');
      if (region && !messageRegionBound){
        region.addEventListener('click', event => {
          const closeBtn = event.target.closest('.app-message__close');
          if (!closeBtn) return;
          const wrapper = closeBtn.closest('.app-message');
          if (wrapper) dismissMessage(wrapper);
        });
        messageRegionBound = true;
      }
      return region;
    }

    function dismissMessage(node){
      if (!node) return;
      if (node.dataset.timeoutId){
        clearTimeout(Number(node.dataset.timeoutId));
        delete node.dataset.timeoutId;
      }
      node.classList.remove('app-message--visible');
      node.classList.add('app-message--closing');
      node.addEventListener('transitionend', () => {
        node.remove();
      }, { once: true });
    }

    function notify(type, text){
      const safeType = ['info','success','warn','error'].includes(type) ? type : 'info';
      const region = getMessageRegion();
      if (!region){
        if (safeType === 'error') console.error(text);
        else console.log(text);
        return;
      }
      const message = document.createElement('div');
      message.className = `app-message app-message--${safeType}`;
      message.setAttribute('role', safeType === 'error' ? 'alert' : 'status');
      const label = document.createElement('span');
      label.textContent = text;
      message.appendChild(label);
      const closeBtn = document.createElement('button');
      closeBtn.type = 'button';
      closeBtn.className = 'app-message__close';
      closeBtn.setAttribute('aria-label', 'Dismiss message');
      closeBtn.textContent = 'Close';
      message.appendChild(closeBtn);
      region.appendChild(message);
      requestAnimationFrame(() => message.classList.add('app-message--visible'));
      const timeoutId = window.setTimeout(() => dismissMessage(message), MESSAGE_TIMEOUT_MS);
      message.dataset.timeoutId = String(timeoutId);
    }


    const FEATURE_KEYS = ['payload','negatives','idempotency','pagination','dataset'];
    const FEATURE_LABELS = {
      payload: 'Payload controls',
      negatives: 'Negative cases',
      idempotency: 'Idempotency checks',
      pagination: 'Pagination toggle',
      dataset: 'Dataset upload'
    };
    const METHOD_RULES = {
      GET: { payload: false, negatives: false, idempotency: false, pagination: true, dataset: true },
      DELETE: { payload: false, negatives: false, idempotency: false, pagination: false, dataset: true },
      HEAD: { payload: false, negatives: false, idempotency: false, pagination: false, dataset: true },
      OPTIONS: { payload: false, negatives: false, idempotency: false, pagination: false, dataset: true },
      POST: { payload: true, negatives: true, idempotency: true, pagination: false, dataset: true },
      PUT: { payload: true, negatives: true, idempotency: true, pagination: false, dataset: true },
      PATCH: { payload: true, negatives: true, idempotency: true, pagination: false, dataset: true }
    };
    const DEFAULT_RULES = { payload: true, negatives: true, idempotency: true, pagination: true, dataset: true };
    const METHOD_NOTES = {
      GET: 'GET requests rarely send a body. Payload, idempotency, and negative-case controls stay hidden unless you need them.',
      DELETE: 'DELETE requests are usually simple. Reveal hidden controls if your API expects more.',
      POST: 'POST requests benefit from payload validation and negative cases. Everything stays visible.',
      PUT: 'PUT requests mirror POST in coverage. Adjust as needed.',
      PATCH: 'PATCH requests often need payload tweaks—negative and idempotency options are available.',
      DEFAULT: 'Adjust any section you need, or reveal hidden ones if this endpoint behaves differently.'
    };

    let featureRegistry = {};
    const manualFeatureOverrides = new Set();
    let currentMethod = null;
    let lastMeta = {};
    let methodUiInitialized = false;

    function registerFeatureNodes(){
      featureRegistry = {};
      FEATURE_KEYS.forEach(feature => {
        featureRegistry[feature] = Array.from(document.querySelectorAll(`[data-feature~="${feature}"]`));
      });
    }

    function disableFeatureControls(el){
      el.querySelectorAll('input, textarea, select, button').forEach(ctrl => {
        if (!ctrl || ctrl.dataset.featureDisabled === '1') return;
        ctrl.dataset.featurePrevDisabled = ctrl.disabled ? '1' : '0';
        ctrl.dataset.featureDisabled = '1';
        ctrl.disabled = true;
        if (ctrl.type === 'checkbox' || ctrl.type === 'radio') ctrl.checked = false;
      });
    }

    function restoreFeatureControls(el){
      el.querySelectorAll('input, textarea, select, button').forEach(ctrl => {
        if (!ctrl || ctrl.dataset.featureDisabled !== '1') return;
        const prev = ctrl.dataset.featurePrevDisabled === '1';
        ctrl.disabled = prev;
        delete ctrl.dataset.featurePrevDisabled;
        delete ctrl.dataset.featureDisabled;
      });
    }

    function setFeatureVisibility(feature, visible){
      const nodes = featureRegistry[feature] || [];
      nodes.forEach(el => {
        if (!el) return;
        if (visible){
          el.removeAttribute('data-feature-hidden');
          el.removeAttribute('aria-hidden');
          restoreFeatureControls(el);
        } else {
          el.setAttribute('data-feature-hidden', 'true');
          el.setAttribute('aria-hidden', 'true');
          disableFeatureControls(el);
        }
      });
    }

    function deriveResource(source){
      if (!source) return null;
      if (typeof source !== 'string' && source.url) source = source.url;
      if (typeof source !== 'string') return null;
      const match = source.match(/https?:\/\/[^\s'"<>]+/i);
      let target = source;
      if (match) target = match[0];
      target = target.replace(/^['"]+|['"]+$/g, '');
      try {
        const url = new URL(target);
        return url.pathname || '/';
      } catch (e) {
        if (target.startsWith('/')) return target;
        return null;
      }
    }

    function deriveAuth(meta){
      if (meta && meta.authHint) return meta.authHint;
      if (meta && meta.hasBearer) return 'bearer';
      const headers = Array.isArray(meta && meta.headers) ? meta.headers : [];
      for (const h of headers){
        const name = String(h.name || '').toLowerCase();
        const value = String(h.value || '');
        if (name === 'authorization'){
          if (/bearer/i.test(value)) return 'bearer';
          if (/basic/i.test(value)) return 'basic';
        }
        if (name === 'x-api-key' || name === 'api-key') return 'api-key';
      }
      const raw = meta && meta.raw ? String(meta.raw) : '';
      if (/authorization:\s*bearer/i.test(raw)) return 'bearer';
      if (/authorization:\s*basic/i.test(raw)) return 'basic';
      if (/x-api-key/i.test(raw)) return 'api-key';
      return null;
    }

    function updateOverviewUI(method, meta = {}, rules = DEFAULT_RULES, hiddenFeatures = []){
      const methodPill = document.getElementById('methodPill');
      if (methodPill){
        methodPill.textContent = 'Method: ' + (method || '--');
        methodPill.dataset.state = method ? 'detected' : 'pending';
        if (method) methodPill.setAttribute('data-method', method);
        else methodPill.removeAttribute('data-method');
      }
      const resourcePill = document.getElementById('resourcePill');
      if (resourcePill){
        const resource = meta.resource || deriveResource(meta.url || meta.raw);
        resourcePill.textContent = 'Resource: ' + (resource || '--');
        resourcePill.dataset.state = resource ? 'detected' : 'pending';
      }
      const authPill = document.getElementById('authPill');
      if (authPill){
        const auth = deriveAuth(meta);
        let label = 'Auth: Unknown';
        let state = 'pending';
        if (auth === 'bearer'){ label = 'Auth: Bearer token'; state = 'ok'; }
        else if (auth === 'basic'){ label = 'Auth: Basic'; state = 'ok'; }
        else if (auth === 'api-key'){ label = 'Auth: API key'; state = 'ok'; }
        else if (auth === 'none'){ label = 'Auth: None'; state = 'warn'; }
        authPill.textContent = label;
        authPill.dataset.state = state;
      }
      const noteEl = document.getElementById('methodNote');
      if (noteEl){
        const base = method ? (METHOD_NOTES[method] || METHOD_NOTES.DEFAULT) : 'Waiting for cURL input.';
        const extras = hiddenFeatures.length ? ' Hidden: ' + hiddenFeatures.map(f => FEATURE_LABELS[f] || f).join(', ') + '.' : '';
        noteEl.textContent = base + extras;
      }
    }

    function updateFeatureHints(method, hiddenFeatures){
      const hintsBox = document.getElementById('featureHints');
      if (!hintsBox) return;
      hintsBox.innerHTML = '';
      if (!hiddenFeatures || hiddenFeatures.length === 0) return;
      hiddenFeatures.forEach(feature => {
        const pill = document.createElement('div');
        pill.className = 'hint-pill';
        const label = FEATURE_LABELS[feature] || feature;
        const span = document.createElement('span');
        span.textContent = `${label} hidden for ${method || 'this'} request.`;
        pill.appendChild(span);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Show anyway';
        btn.addEventListener('click', () => {
          manualFeatureOverrides.add(feature);
          setDetectedMethod(currentMethod, {});
        });
        pill.appendChild(btn);
        hintsBox.appendChild(pill);
      });
    }

    function applyMethodRules(method, meta){
      const normalized = method && METHOD_RULES[method] ? method : null;
      const rules = Object.assign({}, DEFAULT_RULES, normalized ? METHOD_RULES[normalized] : {});
      const hiddenFeatures = [];
      FEATURE_KEYS.forEach(feature => {
        const allowed = rules[feature] !== false;
        if (!allowed && !manualFeatureOverrides.has(feature)) hiddenFeatures.push(feature);
        setFeatureVisibility(feature, allowed || manualFeatureOverrides.has(feature));
      });
      updateFeatureHints(method, hiddenFeatures);
      updateOverviewUI(method, meta, rules, hiddenFeatures);
      scheduleQualityEvaluation();
    }

    function detectMethodFromCurl(raw){
      if (!raw) return null;
      const m = raw.match(/(?:-X|--request)\s*['"]?([A-Za-z]+)/i);
      if (m && m[1]) return m[1].toUpperCase();
      const dataFlag = /(?:--data(?:-raw|-binary|-urlencode)?|-d)\s+/i;
      if (dataFlag.test(raw)) return 'POST';
      return 'GET';
    }

    function detectAuthHint(raw){
      if (!raw) return null;
      if (/authorization:\s*bearer/i.test(raw)) return 'bearer';
      if (/authorization:\s*basic/i.test(raw)) return 'basic';
      if (/x-api-key/i.test(raw)) return 'api-key';
      return null;
    }

    function setMethodFromRaw(raw){
      const trimmed = (raw || '').trim();
      if (!trimmed){
        manualFeatureOverrides.clear();
        lastMeta = {};
        setDetectedMethod(null, {});
        return;
      }
      const method = detectMethodFromCurl(trimmed);
      const resource = deriveResource(trimmed);
      const authHint = detectAuthHint(trimmed);
      setDetectedMethod(method, { resource, raw: trimmed, authHint });
    }

    function setDetectedMethod(method, meta = {}){
      const normalized = method ? String(method).toUpperCase() : null;
      if (normalized && currentMethod && normalized !== currentMethod) {
        manualFeatureOverrides.clear();
      }
      currentMethod = normalized;
      if (normalized) {
        lastMeta = Object.assign({}, lastMeta, meta);
      } else {
        lastMeta = Object.assign({}, meta);
      }
      applyMethodRules(currentMethod, lastMeta);
    }

    function initMethodAwareUI(){
      if (methodUiInitialized) return;
      methodUiInitialized = true;
      registerFeatureNodes();
      setDetectedMethod(null, {});
    }

    const WIZARD_KEY = 'qa-generator-onboarded';
    const USAGE_KEY = 'qa-generator-usage';
    const WIZARD_STEPS = [
      {
        title: 'Welcome product & QA teams',
        body: 'Paste any authenticated cURL and we will extract method, headers, and payload so non-automation QAs can stay focused on coverage rather than syntax.'
      },
      {
        title: 'Triage what matters',
        body: 'The blueprint cards hide controls that do not apply to the detected HTTP method. You can always re-show a section from the hint pills if your API behaves differently.'
      },
      {
        title: 'Enrich coverage with guardrails',
        body: 'Use the Assertions and Expected Outcomes groups to add negative, auth, and idempotency checks. Guardrail hints will call out gaps before you hand off to automation.'
      },
      {
        title: 'Generate & share hand-offs',
        body: 'When you generate, copy the summary for your squad or export the config JSON. Usage metrics stay local so you can measure adoption.'
      }
    ];

    const TEMPLATE_LIBRARY = [
      {
        id: 'get-orders',
        name: 'GET Orders listing',
        description: 'Read-only endpoint that uses pagination and auth expectations.',
        form: {
          curl: "curl --request GET 'https://api.example.com/orders?page=1&limit=20' -H 'Authorization: Bearer ${API_TOKEN}' -H 'Accept: application/json'",
          notes: 'List orders should default to most recent first. No side effects expected.',
          queryParams: 'page=1\nlimit=20',
          extraHeaders: 'accept: application/json',
          includeNegatives: 'false',
          includeTypeNegatives: 'false',
          includeNullNegatives: 'false',
          includeIdempotency: 'false',
          includePagination: 'true',
          expStatusPositive: '200',
          assertions: 'JSONPATH_EXISTS|$.data|\nJSONPATH_LENGTH_GTE|$.data|1\nJSONPATH_EXISTS|$.meta.pagination.total|',
          expJsonAuth: '{"error":{"code":"unauthorized"}}',
          expStatusAuth: '401'
        }
      },
      {
        id: 'post-profile',
        name: 'POST Profile update',
        description: 'Classic write scenario with manual payload and negative requirements.',
        form: {
          curl: "curl --request POST 'https://api.example.com/profile' -H 'Authorization: Bearer ${API_TOKEN}' -H 'Content-Type: application/json' --data '{\"name\":\"QA Example\",\"language\":\"en\"}'",
          notes: 'Ensure validation for name and language plus auth fallback.',
          manualJson: '{\n  "name": "QA Example",\n  "language": "en"\n}',
          includeNegatives: 'true',
          includeTypeNegatives: 'true',
          includeNullNegatives: 'true',
          includeIdempotency: 'true',
          includePagination: 'false',
          requiredFields: 'name\nlanguage',
          lengthRules: 'name|3|30',
          assertions: 'JSONPATH_EQUALS|$.status|success\nJSONPATH_EXISTS|$.data.id|',
          expStatusPositive: '200',
          expStatusNegative: '422',
          expJsonNegative: '{"error":{"field":"name","message":"required"}}',
          expStatusAuth: '401'
        }
      },
      {
        id: 'patch-inventory',
        name: 'PATCH Inventory adjust',
        description: 'Partial update with chained call to verify downstream sku fetch.',
        form: {
          curl: "curl --request PATCH 'https://api.example.com/inventory/sku-123' -H 'Authorization: Bearer ${API_TOKEN_STAFF}' -H 'Content-Type: application/json' --data '{\"quantity\":5}'",
          manualJson: '{\n  "quantity": 5\n}',
          notes: 'Adjust inventory for staff channel and confirm chain fetch uses remembered id.',
          chain: JSON.stringify([
            { method: 'GET', path: '/inventory/sku-123', body: '', rememberPath: '$.data.quantity', rememberKey: 'quantity' }
          ]),
          includeNegatives: 'true',
          includeTypeNegatives: 'true',
          includeNullNegatives: 'true',
          includeIdempotency: 'true',
          includePagination: 'false',
          assertions: 'JSONPATH_NUMBER_GTE|$.data.quantity|0\nJSONPATH_EXISTS|$.data.updated_at|',
          expStatusPositive: '200',
          expStatusNegative: '409',
          expJsonNegative: '{"error":{"code":"limits_exceeded"}}',
          requiredFields: 'quantity'
        }
      }
    ];

    const RUN_POLL_INTERVAL_MS = 4000;
    let currentFeaturePath = null;
    let currentFeatureContent = '';
    let featureStructure = null;
    let currentRunId = null;
    let currentRunToken = null;
    let currentRunPollTimer = null;
    let currentRunStatus = null;
    let stepSections = [];
    let stepButtons = [];
    let currentStep = 0;

    let wizardIndex = 0;

    function ensureWizardSteps(){
      const steps = document.getElementById('wizardSteps');
      if (!steps) return;
      steps.innerHTML = '';
      WIZARD_STEPS.forEach((_, idx) => {
        const span = document.createElement('span');
        span.dataset.index = String(idx);
        steps.appendChild(span);
      });
    }

    function renderWizardStep(){
      const overlay = document.getElementById('onboardingOverlay');
      if (!overlay) return;
      const steps = document.getElementById('wizardSteps');
      const content = document.getElementById('wizardContent');
      if (!content || !steps) return;
      steps.querySelectorAll('span').forEach((el, idx) => {
        el.classList.toggle('active', idx === wizardIndex);
      });
      const step = WIZARD_STEPS[wizardIndex];
      const wrapper = document.createElement('div');
      wrapper.className = 'wizard-step active';
      const title = document.createElement('h3');
      title.id = 'wizardTitle';
      title.textContent = step.title;
      const body = document.createElement('p');
      body.style.margin = '0';
      body.style.color = '#50636c';
      body.style.fontSize = '14px';
      body.textContent = step.body;
      wrapper.appendChild(title);
      wrapper.appendChild(body);
      content.innerHTML = '';
      content.appendChild(wrapper);
      const backBtn = document.getElementById('wizardBack');
      const nextBtn = document.getElementById('wizardNext');
      if (backBtn) backBtn.disabled = wizardIndex === 0;
      if (nextBtn) nextBtn.textContent = wizardIndex === WIZARD_STEPS.length - 1 ? 'Finish' : 'Next';
    }

    function showWizard(force = false){
      const overlay = document.getElementById('onboardingOverlay');
      if (!overlay) return;
      if (!force && localStorage.getItem(WIZARD_KEY) === 'done') return;
      ensureWizardSteps();
      wizardIndex = 0;
      renderWizardStep();
      overlay.style.display = 'flex';
    }

    function hideWizard(markDone = true){
      const overlay = document.getElementById('onboardingOverlay');
      if (overlay) overlay.style.display = 'none';
      if (markDone) {
        try { localStorage.setItem(WIZARD_KEY, 'done'); } catch (e) {}
      }
    }

    function populateTemplatesModal(){
      const grid = document.getElementById('templateGrid');
      if (!grid) return;
      grid.innerHTML = '';
      TEMPLATE_LIBRARY.forEach(t => {
        const card = document.createElement('div');
        card.className = 'template-card';
        const title = document.createElement('strong');
        title.textContent = t.name;
        const desc = document.createElement('p');
        desc.textContent = t.description;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn small';
        btn.textContent = 'Load example';
        btn.addEventListener('click', () => {
          loadTemplate(t);
          hideTemplates();
          setTimeout(() => evaluateQuality(), 100);
        });
        card.appendChild(title);
        card.appendChild(desc);
        card.appendChild(btn);
        grid.appendChild(card);
      });
    }

    function showTemplates(){
      const overlay = document.getElementById('templatesOverlay');
      if (!overlay) return;
      populateTemplatesModal();
      showModal(overlay, { initialFocus: '#templatesClose' });
    }

    function hideTemplates(){
      const overlay = document.getElementById('templatesOverlay');
      if (overlay) hideModal(overlay);
    }

    function setFieldValue(selector, value){
      const field = document.querySelector(selector);
      if (!field) return;
      if (field.type === 'checkbox'){
        field.checked = value === 'true' || value === true;
        field.dispatchEvent(new Event('change', { bubbles:true }));
      } else if (field.type === 'file'){
        // skip file inputs
      } else {
        field.value = value;
        field.dispatchEvent(new Event('input', { bubbles:true }));
      }
    }

    function loadTemplate(template){
      if (!template || !template.form) return;
      manualFeatureOverrides.clear();
      Object.entries(template.form).forEach(([key, val]) => {
        if (key === 'curl') setFieldValue('textarea[name="curl"]', val);
        else if (key === 'notes') setFieldValue('textarea[name="notes"]', val);
        else if (key === 'queryParams') setFieldValue('textarea[name="queryParams"]', val);
        else if (key === 'extraHeaders') setFieldValue('textarea[name="extraHeaders"]', val);
        else if (key === 'manualJson') setFieldValue('textarea[name="manualJson"]', val);
        else if (key === 'lengthRules') setFieldValue('#lengthRules', val);
        else if (key === 'requiredFields') setFieldValue('#requiredFields', val);
        else if (key === 'assertions') {
          setFieldValue('#assertions', val);
          assertionsArr.splice(0, assertionsArr.length, ...val.split('\n').filter(Boolean));
          renderAssertions();
        }
        else if (key === 'chain') {
          try { chainArr.splice(0, chainArr.length, ...JSON.parse(val)); } catch (e) {}
          renderChain();
        }
        else {
          const input = document.querySelector(`[name="${key}"]`);
          if (input) setFieldValue(`[name="${key}"]`, val);
        }
      });
      // After load, detect method from cURL
      const curlValue = template.form.curl || '';
      setTimeout(() => setMethodFromRaw(curlValue), 10);
    }

    let qualityTimer = null;
    function scheduleQualityEvaluation(){
      if (qualityTimer) clearTimeout(qualityTimer);
      qualityTimer = setTimeout(() => evaluateQuality(), 200);
    }

    function evaluateQuality(){
      const hintBox = document.getElementById('qualityHint');
      if (!hintBox) return;
      const form = document.querySelector('form');
      if (!form){ hintBox.style.display = 'none'; return; }
      const messages = [];
      const method = currentMethod;
      const assertions = assertionsArr.filter(Boolean);
      const datasetSelected = (() => {
        const fileInput = form.querySelector('input[name="dataset"]');
        return !!(fileInput && fileInput.files && fileInput.files.length > 0 && fileInput.files[0] && fileInput.files[0].size > 0);
      })();
      const negativesEnabled = document.querySelector('input[name="includeNegatives"]')?.checked;
      const typeNegEnabled = document.querySelector('input[name="includeTypeNegatives"]')?.checked;
      const nullNegEnabled = document.querySelector('input[name="includeNullNegatives"]')?.checked;
      const idemEnabled = document.querySelector('input[name="includeIdempotency"]')?.checked;
      const paginationEnabled = document.querySelector('input[name="includePagination"]')?.checked;
      const manualJson = document.querySelector('textarea[name="manualJson"]')?.value.trim();
      const requiredFields = document.getElementById('requiredFields')?.value.trim();
      const hasSampleResponse = (() => {
        const expPos = document.getElementById('expJsonPositive');
        const expSample = document.getElementById('assertSample');
        const expIdem = document.getElementById('expJsonIdem');
        const expAuth = document.getElementById('expJsonAuth');
        const expNeg = document.getElementById('expJsonNegative');
        return [expPos, expIdem, expAuth, expNeg, expSample].some(el => el && el.value && el.value.trim().length > 0);
      })();
      if ((method === 'POST' || method === 'PUT' || method === 'PATCH') && !manualJson && !datasetSelected){
        messages.push('Provide a manual JSON payload or dataset so downstream testers understand the write contract.');
      }
      if (negativesEnabled && !requiredFields){
        messages.push('Negative cases are enabled. Consider listing required fields or upload a dataset to scope validation.');
      }
      if ((typeNegEnabled || nullNegEnabled) && !manualJson && !datasetSelected){
        messages.push('Advanced negatives rely on a sample payload. Paste manual JSON or upload a dataset to mutate.');
      }
      if (assertions.length === 0){
        if (hasSampleResponse){
          messages.push('Baseline assertions will be generated from your sample response. Add custom rules for business-specific checks.');
        } else {
          messages.push('Add at least one assertion so automation has a deterministic pass/fail signal.');
        }
      }
      if ((method === 'GET' || method === 'DELETE') && manualJson){
        messages.push('This method rarely needs a payload. Remove the manual JSON unless your API truly expects one.');
      }
      if (paginationEnabled && !/page=/i.test(document.querySelector('textarea[name="queryParams"]')?.value || '')){
        messages.push('You enabled pagination cases but no page param is present. Add it or disable pagination.');
      }
      if (messages.length === 0){
        hintBox.style.display = 'none';
      } else {
        hintBox.innerHTML = `<strong>Guardrails:</strong> ${messages.join(' ')} `;
        hintBox.style.display = 'block';
      }
    }

    function loadUsage(){
      try {
        const data = JSON.parse(localStorage.getItem(USAGE_KEY) || '{}');
        return data && typeof data === 'object' ? data : {};
      } catch (e) { return {}; }
    }

    function saveUsage(data){
      try { localStorage.setItem(USAGE_KEY, JSON.stringify(data)); } catch (e) {}
    }

    function updateUsagePill(){
      const pill = document.getElementById('usagePill');
      if (!pill) return;
      const usage = loadUsage();
      const generated = usage.generated || 0;
      const last = usage.lastGenerated ? new Date(usage.lastGenerated) : null;
      if (generated > 0){
        pill.style.display = 'inline-flex';
        pill.textContent = generated === 1 ? '1 feature generated' : `${generated} features generated`;
        if (last){
          const diff = Math.round((Date.now() - last.getTime()) / 60000);
          pill.title = diff <= 0 ? 'Just now' : `${diff} min ago`;
        }
      } else {
        pill.style.display = 'none';
      }
    }

    function buildSummary(){
      const form = document.querySelector('form');
      if (!form) return '';
      const getVal = sel => {
        const el = form.querySelector(sel);
        if (!el) return '';
        if (el.type === 'checkbox') return el.checked ? 'yes' : 'no';
        return el.value || '';
      };
      const summary = [
        `Method: ${currentMethod || 'n/a'}`,
        `Endpoint: ${deriveResource(getVal('textarea[name="curl"]')) || 'n/a'}`,
        `Negatives: ${getVal('input[name="includeNegatives"]')}`,
        `Type mismatch negatives: ${getVal('input[name="includeTypeNegatives"]')}`,
        `Null negatives: ${getVal('input[name="includeNullNegatives"]')}`,
        `Idempotency: ${getVal('input[name="includeIdempotency"]')}`,
        `Assertions: ${assertionsArr.length}`,
        `Dataset selected: ${(() => {
          const fileInput = form.querySelector('input[name="dataset"]');
          return fileInput && fileInput.files && fileInput.files.length ? fileInput.files[0].name : 'no';
        })()}`
      ];
      const notes = getVal('textarea[name="notes"]');
      if (notes) summary.push(`Notes: ${notes}`);
      return summary.join('\n');
    }

    function refreshSummary(){
      const panel = document.getElementById('summaryPanel');
      if (!panel) return;
      panel.textContent = buildSummary();
    }

    function buildConfig(){
      const form = document.querySelector('form');
      if (!form) return {};
      const data = {};
      new FormData(form).forEach((value, key) => {
        if (key === 'dataset') return;
        if (data[key]){
          if (Array.isArray(data[key])) data[key].push(value);
          else data[key] = [data[key], value];
        } else {
          data[key] = value;
        }
      });
      data.method = currentMethod;
      data.assertionsDecoded = assertionsArr;
      data.chainDecoded = chainArr;
      return data;
    }


    function clearRunPoll(){
      if (currentRunPollTimer){
        clearTimeout(currentRunPollTimer);
        currentRunPollTimer = null;
      }
    }

    function scheduleRunPoll(){
      clearRunPoll();
      if (!currentRunId) return;
      currentRunPollTimer = window.setTimeout(pollRunStatus, RUN_POLL_INTERVAL_MS);
    }

    function setRunStatusHtml(html, type){
      const el = document.getElementById('featureRunStatus');
      if (!el) return;
      el.className = 'status-message status-message--run';
      if (!html){
        el.style.display = 'none';
        el.innerHTML = '';
        return;
      }
      if (type) el.classList.add(type);
      el.style.display = 'block';
      el.innerHTML = html;
    }

    function setRunStatusText(text, type){
      if (!text){
        setRunStatusHtml('', null);
        return;
      }
      setRunStatusHtml(escapeHtml(text), type);
    }

    function shortRunId(id){
      if (!id) return '--';
      return String(id).split('-')[0];
    }

    function formatTimestamp(value){
      if (!value) return '--';
      try {
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return String(value);
        return d.toLocaleString();
      } catch (err){
        return String(value);
      }
    }

    async function startRun(payload, statusMessage){
      const body = payload && typeof payload === 'object' ? { ...payload } : {};
      const featurePath = body.featurePath || currentFeaturePath;
      if (!featurePath){
        notify('warn', 'Generate a feature first.');
        return;
      }
      body.featurePath = featurePath;
      setRunStatusText(statusMessage || 'Starting run…', 'info');
      clearRunPoll();
      currentRunId = null;
      currentRunToken = null;
      currentRunStatus = null;
      try {
        const res = await fetch('/api/tests/run', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        if (res.status === 202){
          const view = await res.json();
          currentRunId = view && view.id ? view.id : null;
          currentRunToken = view && view.accessToken ? view.accessToken : null;
          renderRunStatus(view);
          if (currentRunId){
            scheduleRunPoll();
          }
        } else {
          let message = 'Failed to start run';
          try {
            const json = await res.json();
            if (json && json.error) message = json.error;
          } catch (ignoreJson) {
            try {
              const text = await res.text();
              if (text) message = text;
            } catch (_){}
          }
          setRunStatusText(message, 'error');
        }
      } catch (err){
        setRunStatusText('Failed to start run: ' + err.message, 'error');
      }
    }

    async function triggerRunForCurrentFeature(){
      await startRun({ featurePath: currentFeaturePath }, 'Starting run…');
    }

    async function pollRunStatus(){
      if (!currentRunId) return;
      try {
        const pollHeaders = currentRunToken ? {'X-Run-Token': currentRunToken} : undefined;
        const res = await fetch('/api/tests/' + encodeURIComponent(currentRunId), pollHeaders ? { headers: pollHeaders } : undefined);
        if (res.status === 404){
          setRunStatusText('Run not found', 'error');
          clearRunPoll();
          currentRunId = null;
          return;
        }
        if (res.status === 401){
          setRunStatusText('Session expired. Please sign in again.', 'error');
          clearRunPoll();
          currentRunId = null;
          try { window.location.href = '/login'; } catch (e) {}
          return;
        }
        if (res.status === 504){
          scheduleRunPoll();
          return;
        }
        if (!res.ok){
          throw new Error('HTTP ' + res.status);
        }
        const view = await res.json();
        renderRunStatus(view);
        if (view && (view.status === 'RUNNING' || view.status === 'QUEUED')){
          scheduleRunPoll();
        } else {
          clearRunPoll();
        }
      } catch (err){
        setRunStatusText('Failed to poll run: ' + err.message, 'error');
        scheduleRunPoll();
      }
    }

    function renderRunStatus(view){
      if (!view){
        setRunStatusHtml('', null);
        return;
      }
      if (view.id) currentRunId = view.id;
      currentRunStatus = view.status;
      let type = 'info';
      if (view.status === 'SUCCEEDED') type = 'success';
      else if (view.status === 'FAILED') type = 'error';
      const parts = [];
      parts.push('<strong>Run ' + escapeHtml(shortRunId(view.id)) + '</strong>');
      const statusLabel = String(view.status || 'UNKNOWN').toLowerCase();
      parts.push(escapeHtml(statusLabel));
      if (Array.isArray(view.tags) && view.tags.length){
        parts.push('tags: ' + view.tags.map(escapeHtml).join(', '));
      }
      if (Array.isArray(view.names) && view.names.length){
        parts.push('scenario: ' + view.names.map(escapeHtml).join(', '));
      }
      if (view.exitCode !== null && view.exitCode !== undefined){
        parts.push('exit ' + escapeHtml(String(view.exitCode)));
      }
      const when = view.completedAt || view.startedAt || view.createdAt;
      if (when){
        parts.push(formatTimestamp(when));
      }
      let html = parts.join(' · ');
      if (view.id){
        html += ' <button type="button" class="btn tiny ghost" data-run-log="' + escapeHtml(String(view.id)) + '">View log</button>';
      }
      const summary = buildRunFailureSummary(view);
      setRunStatusHtml(html + summary, type);
    }

    function buildRunFailureSummary(view){
      const scenarios = Array.isArray(view && view.scenarios) ? view.scenarios : [];
      if (!scenarios.length) return '';
      const limit = 3;
      const items = scenarios.slice(0, limit).map(entry => {
        const feature = entry.feature && entry.feature !== entry.scenario ? `<span class="run-status-summary__feature">${escapeHtml(entry.feature)}</span>` : '';
        const name = escapeHtml(entry.scenario || 'Scenario');
        const step = entry.step ? `<span class="run-status-summary__step">${escapeHtml(entry.step)}</span>` : '';
        const summary = entry.summary || entry.message || '';
        const msg = summary ? `<span class="run-status-summary__msg">${escapeHtml(summary)}</span>` : '';
        const details = renderFailureDetails(entry, 'run-status-summary__details');
        return `<div class="run-status-summary__item">${feature}<strong>${name}</strong>${step}${msg}${details}</div>`;
      }).join('');
      const more = scenarios.length > limit ? `<div class="run-status-summary__more">+${scenarios.length - limit} more failures…</div>` : '';
      return `<div class="run-status-summary">${items}${more}</div>`;
    }

    function renderFailureDetails(entry, baseClass){
      const list = Array.isArray(entry && entry.details) ? entry.details.slice(0, 3) : [];
      if (!list.length) return '';
      const items = list.map(line => {
        const lower = String(line || '').toLowerCase();
        let extra = '';
        if (lower.includes('expected')) extra = ' expected';
        else if (lower.includes('actual')) extra = ' actual';
        return `<li class="${baseClass}__line${extra}">${escapeHtml(String(line || ''))}</li>`;
      }).join('');
      return `<ul class="${baseClass}">${items}</ul>`;
    }

    function scenarioNameFromStructure(sc){
      if (!sc) return null;
      const fromTitle = extractScenarioName(sc.title);
      if (fromTitle) return fromTitle;
      const lines = (sc.content || '').split('\n');
      for (const raw of lines){
        const name = extractScenarioName(raw);
        if (name) return name;
      }
      return null;
    }

    function extractScenarioName(line){
      if (!line) return null;
      const match = String(line).trim().match(/^(Scenario(?: Outline)?):\s*(.+)$/i);
      if (match && match[2]){
        return match[2].trim();
      }
      return null;
    }

    function scenarioTagsFromStructure(sc){
      if (!sc || !sc.content) return [];
      const set = new Set();
      const lines = sc.content.split('\n');
      for (const raw of lines){
        const trimmed = raw.trim();
        if (!trimmed.startsWith('@')) continue;
        trimmed.split(/\s+/).forEach(token => {
          if (token && token.startsWith('@')) set.add(token);
        });
      }
      return Array.from(set);
    }

    async function runScenarioByIndex(idx){
      if (!featureStructure || !featureStructure.scenarios){
        notify('warn', 'Generate a feature first.');
        return;
      }
      const sc = featureStructure.scenarios[idx];
      if (!sc){
        notify('error', 'Scenario not found.');
        return;
      }
      if (!currentFeaturePath){
        notify('warn', 'Generate a feature first.');
        return;
      }
      const name = scenarioNameFromStructure(sc);
      const tags = scenarioTagsFromStructure(sc);
      const payload = { featurePath: currentFeaturePath };
      if (name) payload.names = [name];
      if (tags.length) payload.tags = tags;
      const label = name || ('Scenario ' + (idx + 1));
      await startRun(payload, 'Starting "' + label + '"…');
    }

    function bindScenarioRunButtons(){
      const box = document.getElementById('featurePreviewBox');
      if (!box) return;
      const buttons = box.querySelectorAll('[data-run-scenario]');
      buttons.forEach(btn => {
        if (btn.dataset.bound) return;
        btn.addEventListener('click', () => {
          const raw = btn.getAttribute('data-run-scenario');
          const idx = Number(raw);
          if (!Number.isNaN(idx)) runScenarioByIndex(idx);
        });
        btn.dataset.bound = '1';
      });
    }

    function toggleRunHistory(){
      const panel = document.getElementById('runHistoryPanel');
      if (!panel) return;
      if (panel.style.display === 'none' || panel.style.display === ''){
        loadRunHistory();
      } else {
        panel.style.display = 'none';
      }
    }

    async function loadRunHistory(){
      const panel = document.getElementById('runHistoryPanel');
      if (!panel) return;
      panel.style.display = 'block';
      panel.dataset.mode = 'history';
      panel.innerHTML = '<div class="run-history__header"><strong>Recent runs</strong><button type="button" class="btn tiny ghost" data-action="close-run-panel">Close</button></div><div class="run-history__body">Loading…</div>';
      try {
        const res = await fetch('/api/tests');
        if (!res.ok){
          throw new Error('HTTP ' + res.status);
        }
        const data = await res.json();
        const body = panel.querySelector('.run-history__body');
        body.innerHTML = renderRunHistoryList(Array.isArray(data) ? data : []);
      } catch (err){
        const body = panel.querySelector('.run-history__body');
        if (body) body.innerHTML = '<div class="err">' + escapeHtml('Failed to load runs: ' + err.message) + '</div>';
      }
    }

    function renderRunHistoryList(runs){
      if (!Array.isArray(runs) || runs.length === 0){
        return '<div class="hint">No runs yet.</div>';
      }
      const items = runs.slice(0, 12).map(renderRunHistoryItem).join('');
      return '<ul class="run-history__list">' + items + '</ul>';
    }

    function renderRunHistoryItem(run){
      const id = escapeHtml(shortRunId(run && run.id));
      const statusLabel = String(run && run.status ? run.status : 'UNKNOWN').toLowerCase();
      const status = escapeHtml(statusLabel);
      const when = escapeHtml(formatTimestamp(run && (run.completedAt || run.startedAt || run.createdAt)));
      const path = run && run.featurePath ? '<div class="run-history__path">' + escapeHtml(run.featurePath) + '</div>' : '';
      let tags = '';
      if (run && Array.isArray(run.tags) && run.tags.length){
        tags = '<div class="run-history__tags">tags: ' + run.tags.map(escapeHtml).join(', ') + '</div>';
      }
      let names = '';
      if (run && Array.isArray(run.names) && run.names.length){
        names = '<div class="run-history__names">scenario: ' + run.names.map(escapeHtml).join(', ') + '</div>';
      }
      const failures = buildHistoryFailureSummary(run);
      const logBtn = run && run.id ? '<button type="button" class="btn tiny ghost" data-run-log="' + escapeHtml(String(run.id)) + '">View log</button>' : '';
      return '<li>' +
             '<div><strong>' + id + '</strong> · ' + status + ' · ' + when + '</div>' +
             path + tags + names + failures +
             '<div class="run-history__actions">' + logBtn + '</div>' +
             '</li>';
    }

    function buildHistoryFailureSummary(run){
      const scenarios = Array.isArray(run && run.scenarios) ? run.scenarios : [];
      if (!scenarios.length) return '';
      const limit = 2;
      const items = scenarios.slice(0, limit).map(entry => {
        const feature = entry.feature && entry.feature !== entry.scenario ? `<span class="run-history__failure-feature">${escapeHtml(entry.feature)}</span>` : '';
        const name = escapeHtml(entry.scenario || 'Scenario');
        const summary = entry.summary || entry.message || '';
        const msg = summary ? `<span class="run-history__failure-msg">${escapeHtml(summary)}</span>` : '';
        const details = renderFailureDetails(entry, 'run-history__failure-list');
        return `<div class="run-history__failure">${feature}<strong>${name}</strong>${msg}${details}</div>`;
      }).join('');
      const more = scenarios.length > limit ? `<div class="run-history__more">+${scenarios.length - limit} more failures…</div>` : '';
      return `<div class="run-history__failures">${items}${more}</div>`;
    }

    async function showRunLog(runId){
      const panel = document.getElementById('runHistoryPanel');
      if (!panel) return;
      panel.style.display = 'block';
      panel.dataset.mode = 'log';
      panel.innerHTML = '<div class="run-history__header"><strong>Run ' + escapeHtml(shortRunId(runId)) + ' log</strong><div class="run-history__header-actions"><button type="button" class="btn tiny ghost" data-action="back-to-history">History</button><button type="button" class="btn tiny ghost" data-action="close-run-panel">Close</button></div></div><pre class="run-history__log">Loading…</pre>';
      try {
        const logHeaders = currentRunToken ? {'X-Run-Token': currentRunToken} : undefined;
        const res = await fetch('/api/tests/' + encodeURIComponent(runId) + '/log', logHeaders ? { headers: logHeaders } : undefined);
        const logEl = panel.querySelector('.run-history__log');
        if (!res.ok){
          let message = 'Unable to load log (HTTP ' + res.status + ')';
          try {
            const text = await res.text();
            if (text) message = text;
          } catch (ignoreText) {}
          if (logEl) logEl.textContent = message;
          return;
        }
        const text = await res.text();
        if (logEl) logEl.textContent = text || '(log is empty)';
      } catch (err){
        const logEl = panel.querySelector('.run-history__log');
        if (logEl) logEl.textContent = 'Failed to load log: ' + err.message;
      }
    }

    function bindRunUi(){
      const runBtn = document.getElementById('runFeatureBtn');
      if (runBtn && !runBtn.dataset.bound){
        runBtn.addEventListener('click', triggerRunForCurrentFeature);
        runBtn.dataset.bound = '1';
      }
      const historyBtn = document.getElementById('openRunHistory');
      if (historyBtn && !historyBtn.dataset.bound){
        historyBtn.addEventListener('click', toggleRunHistory);
        historyBtn.dataset.bound = '1';
      }
      const statusEl = document.getElementById('featureRunStatus');
      if (statusEl && !statusEl.dataset.bound){
        statusEl.addEventListener('click', (event) => {
          const btn = event.target.closest('[data-run-log]');
          if (btn){
            const runId = btn.getAttribute('data-run-log');
            if (runId) showRunLog(runId);
          }
        });
        statusEl.dataset.bound = '1';
      }
      const panel = document.getElementById('runHistoryPanel');
      if (panel && !panel.dataset.bound){
        panel.addEventListener('click', (event) => {
          const actionBtn = event.target.closest('[data-action]');
          if (actionBtn){
            const action = actionBtn.getAttribute('data-action');
            if (action === 'close-run-panel'){
              panel.style.display = 'none';
              return;
            }
            if (action === 'back-to-history'){
              loadRunHistory();
              return;
            }
          }
          const logBtn = event.target.closest('[data-run-log]');
          if (logBtn){
            const runId = logBtn.getAttribute('data-run-log');
            if (runId) showRunLog(runId);
          }
        });
        panel.dataset.bound = '1';
      }
      bindScenarioRunButtons();
    }

    function renderFeatureOutput(json){
      const out = document.getElementById('out');
      if (!out) return;
      currentFeaturePath = json.path || null;
      currentFeatureContent = json.featurePreview || '';
      featureStructure = parseFeature(currentFeatureContent);
      clearRunPoll();
      currentRunId = null;
      currentRunStatus = null;
      const warningsHtml = computeFeatureWarnings(json);
      let html = '';
      html += '<div class="alert success">✅ Created: ' + escapeHtml(currentFeaturePath || 'feature.feature') + '</div>';
      html += '<div class="feature-preview-actions">' +
              '<button class="btn small" type="button" onclick="saveFeatureEdit()">Save changes</button>' +
              '<button class="btn small" type="button" onclick="downloadEditedFeature()">Download edited feature</button>' +
              '<button class="btn small" type="button" onclick="addCustomScenario()">Add scenario</button>' +
              '<button class="btn small" type="button" onclick="deleteLastScenario()">Delete last scenario</button>' +
              '</div>';
      html += '<div id="featureStatusMessage" class="status-message"></div>';
      html += '<div class="feature-run-actions">' +
              '<button class="btn small" type="button" id="runFeatureBtn">Run generated test</button>' +
              '<button class="btn small ghost" type="button" id="openRunHistory">Runs history</button>' +
              '</div>';
      html += '<div id="featureRunStatus" class="status-message status-message--run"></div>';
      html += '<div id="runHistoryPanel" class="run-history" style="display:none"></div>';
      html += '<div id="featurePreviewBox" class="feature-preview-box"></div>';
      if (currentFeaturePath) {
        html += '<div class="hint">Edits are applied to <code>' + escapeHtml(currentFeaturePath) + '</code> when you click Save changes.</div>';
      }
      if (json.assertionsApplied && json.assertionsApplied.length){
        html += '<div class="hint">Assertions applied (' + json.assertionsApplied.length + '): ' + json.assertionsApplied.map(escapeHtml).join(' • ') + '</div>';
      }
      html += warningsHtml;
      out.innerHTML = html;
      setRunStatusHtml('', null);
      bindRunUi();
      bindScenarioRunButtons();
      setFeatureStatus('', '');
      updateScenarioEditors();
      out.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function renderFeatureEditors(structure){
      const headerText = structure.header ?? '';
      let html = '';
      html += '<div class="scenario-editor">'
            + '<header><h4>Feature & background</h4><span>Edit feature description or background</span></header>'
            + '<textarea id="featureHeaderEditor" oninput="updateFeatureHeader()">' + escapeHtml(headerText) + '</textarea>'
            + '</div>';
      structure.scenarios.forEach((sc, idx) => {
        const safeTitle = escapeHtml(sc.title || `Scenario ${idx + 1}`);
        html += '<div class="scenario-editor" data-index="' + idx + '">'
              + '<header><h4 id="scenarioTitle-' + idx + '">' + safeTitle + '</h4><div style="display:flex;align-items:center;gap:8px;"><span>Scenario ' + (idx + 1) + '</span><button type="button" class="btn small" data-run-scenario="' + idx + '">Run</button><button type="button" class="btn small btn-danger" onclick="deleteScenario(' + idx + ')">Delete</button></div></header>'
              + '<textarea id="scenarioEditor-' + idx + '" oninput="updateScenarioTitle(' + idx + ')">' + escapeHtml(sc.content || '') + '</textarea>'
              + '</div>';
      });
      return html;
    }

    function updateScenarioEditors(){
      if (!featureStructure) return;
      const box = document.getElementById('featurePreviewBox');
      if (!box) return;
      box.innerHTML = renderFeatureEditors(featureStructure);
      bindScenarioRunButtons();
    }

    function parseFeature(text){
      const normalized = (text || '').replace(/\r\n/g, '\n');
      const lines = normalized.split('\n');
      const headerLines = [];
      const blocks = [];
      let currentBlock = null;
      let encounteredSection = false;

      const pushCurrent = () => {
        if (currentBlock && currentBlock.length > 0) {
          const raw = currentBlock.join('\n').replace(/\s*$/, '');
          if (raw) blocks.push(raw);
          currentBlock = [];
        }
      };

      for (const line of lines) {
        const trimmed = line.trim();
        const isScenario = /^Scenario/.test(trimmed);
        const isScenarioOutline = /^Scenario Outline/.test(trimmed);
        const isTag = trimmed.startsWith('@');
        const scenarioTrigger = isScenario || isScenarioOutline;

        if (!encounteredSection) {
          if (scenarioTrigger || isTag) {
            encounteredSection = true;
            currentBlock = [];
          } else {
            headerLines.push(line);
            continue;
          }
        }

        if (!currentBlock) currentBlock = [];

        if (scenarioTrigger) {
          const hasScenarioAlready = currentBlock.some(l => l.trim().startsWith('Scenario'));
          if (hasScenarioAlready) pushCurrent();
        } else if (isTag) {
          const hasScenarioAlready = currentBlock.some(l => l.trim().startsWith('Scenario'));
          if (hasScenarioAlready) pushCurrent();
        }

        currentBlock.push(line);
      }

      pushCurrent();

      const scenarios = blocks.map((content, idx) => {
        const lines = content.split('\n');
        let title = lines.find(l => l.trim().startsWith('Scenario'));
        if (title) title = title.trim();
        else title = `Scenario ${idx + 1}`;
        return { index: idx, title, content };
      });

      return {
        header: headerLines.join('\n').replace(/\s*$/, ''),
        scenarios
      };
    }

    function updateFeatureHeader(){
      if (!featureStructure) return;
      const headerEl = document.getElementById('featureHeaderEditor');
      if (headerEl) featureStructure.header = headerEl.value;
    }

    function updateScenarioTitle(idx){
      if (!featureStructure || !featureStructure.scenarios[idx]) return;
      const textarea = document.getElementById('scenarioEditor-' + idx);
      const titleEl = document.getElementById('scenarioTitle-' + idx);
      if (!textarea || !titleEl) return;
      const value = textarea.value;
      featureStructure.scenarios[idx].content = value;
      const lines = value.split('\n');
      let titleLine = lines.find(l => l.trim().startsWith('Scenario'));
      if (titleLine) titleLine = titleLine.trim();
      else titleLine = `Scenario ${idx + 1}`;
      featureStructure.scenarios[idx].title = titleLine;
      titleEl.textContent = titleLine;
    }

    function refreshCurrentFeatureContent(){
      if (!featureStructure) {
        return;
      }
      const headerEl = document.getElementById('featureHeaderEditor');
      if (headerEl) featureStructure.header = headerEl.value;
      featureStructure.scenarios.forEach((sc, idx) => {
        const textarea = document.getElementById('scenarioEditor-' + idx);
        if (textarea) sc.content = textarea.value;
        if (!sc.content) sc.content = '';
        if (!sc.title || sc.title.trim() === '') {
          const lines = (sc.content || '').split('\n');
          let titleLine = lines.find(l => l.trim().startsWith('Scenario'));
          sc.title = titleLine ? titleLine.trim() : `Scenario ${idx + 1}`;
        }
      });
      currentFeatureContent = assembleFeature(featureStructure);
    }

    function assembleFeature(structure){
      const parts = [];
      const header = (structure.header || '').replace(/\r\n/g, '\n').trimEnd();
      if (header) parts.push(header);
      structure.scenarios.forEach(sc => {
        const block = (sc.content || '').replace(/\r\n/g, '\n').trimEnd();
        if (block) parts.push(block);
      });
      return parts.join('\n\n') + '\n';
    }

    function addCustomScenario(){
      if (!featureStructure) return;
      reindexScenarios();
      const nextNumber = featureStructure.scenarios.length + 1;
      const defaultContent = '@custom\nScenario: Custom scenario ' + nextNumber + '\n    When I GET to "/path"\n    Then the response status should be 200';
      featureStructure.scenarios.push({ index: featureStructure.scenarios.length, title: 'Scenario: Custom scenario ' + nextNumber, content: defaultContent });
      reindexScenarios();
      updateScenarioEditors();
      setFeatureStatus('Added custom scenario ' + nextNumber + '. Update the steps and save when ready.', 'success');
      const newEditor = document.getElementById('scenarioEditor-' + (featureStructure.scenarios.length - 1));
      if (newEditor) newEditor.focus();
    }

    function reindexScenarios(){
      if (!featureStructure) return;
      featureStructure.scenarios.forEach((sc, idx) => { sc.index = idx; });
    }

    function deleteLastScenario(){
      deleteScenario(featureStructure ? featureStructure.scenarios.length - 1 : -1);
    }

    function deleteScenario(idx){
      if (!featureStructure || !featureStructure.scenarios || featureStructure.scenarios.length === 0) {
        return;
      }
      if (idx == null || idx < 0 || idx >= featureStructure.scenarios.length){
        idx = featureStructure.scenarios.length - 1;
      }
      featureStructure.scenarios.splice(idx, 1);
      reindexScenarios();
      updateScenarioEditors();
      setFeatureStatus('Removed scenario ' + (idx + 1) + '. Save changes to persist.', 'success');
    }

    function setFeatureStatus(message, type){
      const el = document.getElementById('featureStatusMessage');
      if (!el) return;
      if (!message) {
        el.className = 'status-message';
        el.style.display = 'none';
        el.innerHTML = '';
        return;
      }
      el.className = 'status-message';
      if (type === 'success') {
        el.classList.add('success');
      } else if (type === 'error') {
        el.classList.add('error');
      }
      el.style.display = 'block';
      el.innerHTML = message;
    }

    function computeFeatureWarnings(json){
      let warnings = '';
      if (json.datasetPath){
        warnings += '<div>📄 Dataset: ' + escapeHtml(json.datasetPath) + ' (' + json.datasetRows + ' rows)</div>';
      }
      if (json.datasetMissingHeaders){
        const vals = Array.from(json.datasetMissingHeaders).map(escapeHtml).join(', ');
        warnings += '<div class="err">⚠ Missing in dataset: ' + vals + '</div>';
      }
      if (json.datasetExtraHeaders){
        const vals = Array.from(json.datasetExtraHeaders).map(escapeHtml).join(', ');
        warnings += '<div class="err">⚠ Extra in dataset: ' + vals + '</div>';
      }
      if (json.datasetTypeMismatches){
        const rows = Array.from(json.datasetTypeMismatches).map(m => `${escapeHtml(String(m.field || ''))}: expected ${escapeHtml(String(m.expected || ''))} , got ${escapeHtml(String(m.actual || ''))}`);
        warnings += '<div class="err">⚠ Type mismatches:<br/>' + rows.join('<br/>') + '</div>';
      }
      if (json.missingHeadersFromCurl){
        const vals = Array.from(json.missingHeadersFromCurl).map(escapeHtml).join(', ');
        warnings += '<div class="err">⚠ Headers present in cURL but not added: ' + vals + '</div>';
      }
      return warnings;
    }

    async function saveFeatureEdit(){
      if (!currentFeaturePath){ notify('warn', 'No feature file to update yet.'); return; }
      refreshCurrentFeatureContent();
      try {
        const res = await fetch('/api/feature/update', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ path: currentFeaturePath, content: currentFeatureContent })
        });
        const json = await res.json();
        if (!res.ok || !json.ok){
          throw new Error(json.error || ('HTTP ' + res.status));
        }
        setFeatureStatus('Saved feature to ' + escapeHtml(json.path), 'success');
      } catch (err){
        setFeatureStatus('Failed to save feature: ' + escapeHtml(err.message), 'error');
      }
    }

    function downloadEditedFeature(){
      refreshCurrentFeatureContent();
      const blob = new Blob([currentFeatureContent], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const name = currentFeaturePath ? currentFeaturePath.split(/[\\/]/).pop() : 'feature.feature';
      a.href = url;
      a.download = name;
      document.body.appendChild(a);
      a.click();
      URL.revokeObjectURL(url);
      a.remove();
    }
    window.addEventListener('DOMContentLoaded', () => {
      updateToggle();
      initMethodAwareUI();
      updateUsagePill();
      initTokenSelector();
      initStepper();
      refreshSummary();
      const t = document.getElementById('themeToggle');
      if (t){
        t.addEventListener('click', toggleTheme);
        t.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleTheme(); }});
      }
      const wizardNext = document.getElementById('wizardNext');
      const wizardBack = document.getElementById('wizardBack');
      const wizardSkip = document.getElementById('wizardSkip');
      if (wizardNext) wizardNext.addEventListener('click', () => {
        if (wizardIndex < WIZARD_STEPS.length - 1){
          wizardIndex += 1; renderWizardStep();
        } else {
          hideWizard(true);
        }
      });
      if (wizardBack) wizardBack.addEventListener('click', () => {
        if (wizardIndex > 0){ wizardIndex -= 1; renderWizardStep(); }
      });
      if (wizardSkip) wizardSkip.addEventListener('click', () => hideWizard(true));
      const openWizardBtn = document.getElementById('openWizard');
      if (openWizardBtn) openWizardBtn.addEventListener('click', () => showWizard(true));
      const openTemplatesBtn = document.getElementById('openTemplates');
      if (openTemplatesBtn) openTemplatesBtn.addEventListener('click', () => showTemplates());
      const templatesClose = document.getElementById('templatesClose');
      if (templatesClose) templatesClose.addEventListener('click', hideTemplates);
      const onboardingOverlay = document.getElementById('onboardingOverlay');
      if (onboardingOverlay) onboardingOverlay.addEventListener('click', (e) => { if (e.target === onboardingOverlay) hideWizard(false); });

      // Cmd/Ctrl + Enter to submit
      document.addEventListener('keydown', (e) => {
        if ((e.metaKey || e.ctrlKey) && e.key === 'Enter'){
          const form = document.querySelector('form'); if (form) form.requestSubmit();
        }
      });
      // Initialize generated-cases state based on dataset selection (on load)
      updateGeneratedCasesState();
      // Fetch logged-in user email
      fetch('/api/me').then(r => r.ok ? r.json() : null).then(j => {
        if (j && j.email){
          const box = document.getElementById('userBox');
          const em = document.getElementById('userEmail');
          if (box && em){ em.textContent = j.email; box.style.display = 'block'; }
        }
      }).catch(()=>{});
      // Auto-validate cURL after user pauses typing
      const curlBox = document.querySelector('textarea[name="curl"]');
      if (curlBox){
        const detectDebounced = debounce(() => {
          setMethodFromRaw(curlBox.value);
          scheduleQualityEvaluation();
        }, 250);
        const validateDebounced = debounce(() => {
          const v = curlBox.value || '';
          if (v.trim().toLowerCase().startsWith('curl')) validateCurl();
        }, 600);
        curlBox.addEventListener('input', () => {
          detectDebounced();
          validateDebounced();
        });
        detectDebounced();
        setMethodFromRaw(curlBox.value);
      }
      // Ensure hidden assertions field is synced on submit and wire analytics
      const formEl = document.querySelector('form');
      if (formEl){
        formEl.addEventListener('submit', () => {
          const hidden = document.getElementById('assertions');
          if (hidden) hidden.value = assertionsArr.join('\n');
        });
        formEl.addEventListener('reset', () => {
          manualFeatureOverrides.clear();
          setTimeout(() => {
            const currentCurl = curlBox ? curlBox.value : '';
            setMethodFromRaw(currentCurl);
            evaluateQuality();
          }, 0);
        });
        const onFormChange = () => { scheduleQualityEvaluation(); refreshSummary(); };
        formEl.addEventListener('input', onFormChange, true);
        formEl.addEventListener('change', onFormChange, true);
      }

      const copySummaryBtn = document.getElementById('copySummaryBtn');
      if (copySummaryBtn) copySummaryBtn.addEventListener('click', async () => {
        const text = buildSummary();
        if (!text) { notify('error', 'Fill in the form first.'); return; }
        try {
          await navigator.clipboard.writeText(text);
          copySummaryBtn.textContent = 'Copied!';
          setTimeout(() => copySummaryBtn.textContent = 'Copy hand-off summary', 1500);
        } catch (e) {
          notify('error', 'Clipboard failed. Copy manually: ' + text);
        }
      });
      const downloadConfigBtn = document.getElementById('downloadConfigBtn');
      if (downloadConfigBtn) downloadConfigBtn.addEventListener('click', () => {
        const data = buildConfig();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type:'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const name = data.featureName ? `${data.featureName}.config.json` : 'generator-config.json';
        a.href = url; a.download = name; document.body.appendChild(a); a.click();
        URL.revokeObjectURL(url); a.remove();
      });

      evaluateQuality();
      if (!localStorage.getItem(WIZARD_KEY)) showWizard(false);
      bindRunUi();
      bindScenarioRunButtons();
    });

    window.addEventListener('beforeunload', clearRunPoll);

    function applyPreset(kind){
      const headers = document.getElementById('extraHeaders');
      const tokenEnv = document.querySelector('input[name="tokenEnvVar"]');
      if(kind === 'admin'){
        const preset = [
          'x-client-source: web-dashboard',
          'x-store-id: 10001',
          'accept-language: ar'
        ].join('\n');
        headers.value = preset;
        if (tokenEnv && !tokenEnv.value) tokenEnv.value = 'API_TOKEN_STAFF_EDIT';
      } else if (kind === 'store'){
        headers.value = 'accept-language: ar';
        if (tokenEnv && !tokenEnv.value) tokenEnv.value = 'API_TOKEN_CUSTOMER';
      }
      scheduleQualityEvaluation();
    }
    function showFileInfo(ev){
      const el = document.getElementById('fileInfo');
      const f = ev.target.files && ev.target.files[0];
      if (!el) return;
      if (f){ el.textContent = `Selected: ${f.name} (${Math.round(f.size/1024)} KB)`; }
      else { el.textContent = ''; }
      updateGeneratedCasesState();
    }
    function updateGeneratedCasesState(){
      const fileInput = document.querySelector('input[name="dataset"]');
      const hasFile = !!(fileInput && fileInput.files && fileInput.files.length > 0 && fileInput.files[0] && fileInput.files[0].size > 0);
      ['includeNegatives','includeTypeNegatives','includeNullNegatives','includeIdempotency','includePagination'].forEach(name => {
        const cb = document.querySelector(`input[name="${name}"]`);
        if (cb){
          const locked = cb.dataset.featureDisabled === '1';
          cb.disabled = locked || hasFile;
          if (locked) cb.checked = false;
        }
      });
      const note = document.getElementById('genCasesNote');
      if (note) note.style.display = hasFile ? 'block' : 'none';
      scheduleQualityEvaluation();
    }
    // Small debounce helper for input-driven actions
    function debounce(fn, ms){ let to=null; return (...args)=>{ clearTimeout(to); to=setTimeout(()=>fn.apply(null,args), ms); }; }
    function submitForm(ev){
      ev.preventDefault();
      const form = ev.target;
      if (!form.checkValidity()) { form.reportValidity(); return; }
      // Sync hidden assertions before building FormData
      const hiddenAsserts = form.querySelector('#assertions');
      if (hiddenAsserts) hiddenAsserts.value = (typeof assertionsArr !== 'undefined' && assertionsArr.join) ? assertionsArr.join('\n') : hiddenAsserts.value;
      const fd = new FormData(form);
      // Ensure chain JSON is included
      const chainHidden = form.querySelector('#chain');
      if (chainHidden) chainHidden.value = JSON.stringify(chainArr);
      // Drop empty dataset part to avoid sending an empty octet-stream part
      const fileInput = form.querySelector('input[name="dataset"]');
      if (fileInput && (!fileInput.files || fileInput.files.length === 0 || fileInput.files[0].size === 0)) {
        fd.delete('dataset');
      }
      // Ensure unchecked checkboxes are explicitly sent as false
      ['includeNegatives','includeTypeNegatives','includeNullNegatives','includeIdempotency','includePagination'].forEach(name => {
        if (!fd.has(name)) fd.append(name, 'false');
      });
      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/generate');
      xhr.responseType = 'json';
      xhr.send(fd);
      const out = document.getElementById('out');
      if (out) out.innerHTML = '<div class="alert" style="background:#fff;border:1px dashed var(--border);color:#637b86">⏳ Generating file...</div>'; 
      xhr.onload = () => {
        const json = xhr.response || {};
        if (xhr.status >= 200 && xhr.status < 300 && json.ok) {
          renderFeatureOutput(json);
          const usage = loadUsage();
          usage.generated = (usage.generated || 0) + 1;
          usage.lastGenerated = new Date().toISOString();
          saveUsage(usage);
          updateUsagePill();
        } else {
          out.innerHTML = '<div class="alert danger">❌ ' + (json.error || (xhr.status + ' ' + xhr.statusText)) + '</div>';
        }
      };
      xhr.onerror = () => {
        out.innerHTML = '<div class="alert danger">❌ Network error submitting form</div>';
      };
    }
    function generateXHR(btn){
      const form = btn.closest('form');
      if (!form) return;
      submitForm({ preventDefault: () => {}, target: form });
    }
    // Legacy inline generation removed; preview opens in a new tab.
    async function extractFromCurl(){
      const curl = document.querySelector('textarea[name="curl"]').value;
      if(!curl.trim()){ notify('error', 'Paste a cURL first.'); return; }
      const res = await fetch('/api/suggest', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({curl})});
      const json = await res.json();
      if(json.error){ notify('error', json.error); return; }
      const headersBox = document.querySelector('textarea[name="extraHeaders"]');
      const queryBox = document.querySelector('textarea[name="queryParams"]');
      const manualJsonBox = document.querySelector('textarea[name="manualJson"]');
      if(Array.isArray(json.headers)){
        headersBox.value = json.headers.map(h => `${h.name}: ${h.value}`).join('\n');
      }
      if(Array.isArray(json.query)){
        queryBox.value = json.query.map(p => p.value ? `${p.name}=${p.value}` : `${p.name}`).join('\n');
      }
      if(json.body && (!manualJsonBox.value || !manualJsonBox.value.trim())){
        manualJsonBox.value = json.body;
      }
      const headerList = Array.isArray(json.headers) ? json.headers : [];
      const apiMethod = json.method ? String(json.method).toUpperCase() : null;
      const fallbackMethod = detectMethodFromCurl(curl);
      let authHint = json.hasBearer === true ? 'bearer' : detectAuthHint(curl);
      if (!authHint){
        const hasAuthHeader = headerList.some(h => String(h.name || '').toLowerCase() === 'authorization');
        if (!hasAuthHeader) authHint = 'none';
      }
      setDetectedMethod(apiMethod || fallbackMethod, {
        url: json.url,
        headers: headerList,
        hasBearer: json.hasBearer === true,
        authHint,
        raw: curl
      });
    }
    async function validateCurl(){
      const curl = document.querySelector('textarea[name="curl"]').value;
      const box = document.getElementById('validationBox');
      if(!curl.trim()){ box.style.display='block'; box.className='alert danger'; box.textContent='Paste a cURL first'; return; }
      try{
        const res = await fetch('/api/suggest', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({curl})});
        const json = await res.json();
        if(!res.ok || json.error){ box.style.display='block'; box.className='alert danger'; box.textContent = json.error || ('HTTP '+res.status); return; }
        const warnings = [];
        if(!json.url) warnings.push('URL not detected');
        if(json.hasBearer !== true) warnings.push('No Bearer token detected');
        let bodyOk = true;
        if (json.body) { try{ JSON.parse(json.body); } catch(e){ bodyOk=false; } }
        const parts = [];
        parts.push('Method: ' + (json.method||'?'));
        parts.push('URL: ' + (json.url||'?'));
        parts.push('Headers detected: ' + (Array.isArray(json.headers)? json.headers.length : 0));
        parts.push('Body JSON valid: ' + (json.body ? (bodyOk? 'yes':'no') : 'n/a'));
        if (warnings.length){
          box.className='alert danger';
          box.innerHTML = 'Issues: '+ warnings.join('; ') + '<br/>' + parts.join(' • ');
        } else {
          box.className='alert success';
          box.textContent = 'Looks good. ' + parts.join(' • ');
        }
        box.style.display='block';

        const headerList = Array.isArray(json.headers) ? json.headers : [];
        const apiMethod = json.method ? String(json.method).toUpperCase() : null;
        let authHint = json.hasBearer === true ? 'bearer' : detectAuthHint(curl);
        if (!authHint){
          const hasAuthHeader = headerList.some(h => String(h.name || '').toLowerCase() === 'authorization');
          if (!hasAuthHeader) authHint = 'none';
        }
        setDetectedMethod(apiMethod || detectMethodFromCurl(curl), {
          url: json.url,
          headers: headerList,
          hasBearer: json.hasBearer === true,
          authHint,
          raw: curl
        });

        // Build assertion suggestions
        const sugg = [];
        const headers = Array.isArray(json.headers) ? json.headers : [];
        const hasJsonAccept = headers.some(h => (h.name||'').toLowerCase() === 'accept' && (h.value||'').toLowerCase().includes('application/json'));
        const hasJsonContentType = headers.some(h => (h.name||'').toLowerCase() === 'content-type' && (h.value||'').toLowerCase().includes('application/json'));
        if (hasJsonAccept || hasJsonContentType) {
          sugg.push(['HEADER_EQUALS','Content-Type','application/json']);
        }
        // Generic JSONPath suggestions (common API patterns)
        ['id','data','items','message'].forEach(k => sugg.push(['JSONPATH_EXISTS', '$.'+k, '']));
        // Render chips
        const cont = document.getElementById('assertSuggestions');
        const note = document.getElementById('assertSuggestNote');
        if (cont) {
          cont.innerHTML = '';
          sugg.forEach(([t,a,b]) => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'chip';
            chip.textContent = `${t.split('_')[0]}: ${a}${b? ' = '+b:''}`;
            chip.onclick = () => addAssertionLine(t,a,b);
            cont.appendChild(chip);
          });
        }
        if (note) note.textContent = 'Quick add suggestions — click to add. Adjust to match your API response.';
        // Enable/disable JSONPath inputs note
        setAssertionsJsonEnabled(hasJsonAccept || hasJsonContentType);

        // Pre-populate Sample Response JSON from cURL body for non-GET, if valid and empty
        try{
          const meth = (json.method||'').toUpperCase();
          const sampleBox = document.getElementById('assertSample');
          if (sampleBox && (!sampleBox.value || !sampleBox.value.trim()) && meth !== 'GET' && bodyOk && json.body){
            // Pretty-print if parseable
            const parsed = JSON.parse(json.body);
            sampleBox.value = JSON.stringify(parsed, null, 2);
          }
        }catch(e){ /* ignore */ }
      }catch(err){
        box.style.display='block'; box.className='alert danger'; box.textContent='Validation failed: '+err;
      }
    }
    // Chained requests UI
    const chainArr = [];
    function renderChain(){
      const ul = document.getElementById('chainList');
      const hidden = document.getElementById('chain');
      if (!ul || !hidden) return;
      ul.innerHTML = '';
      chainArr.forEach((s, i) => {
        const li = document.createElement('li');
        li.style.cursor = 'pointer';
        li.title = 'Click to remove';
        const hasRem = s.rememberPath && s.rememberKey;
        li.textContent = `${s.method} ${s.path}${s.body? ' with body':''}${hasRem? ` (remember ${s.rememberPath} as ${s.rememberKey})`:''}`;
        li.onclick = () => { chainArr.splice(i,1); renderChain(); };
        ul.appendChild(li);
      });
      hidden.value = JSON.stringify(chainArr);
      scheduleQualityEvaluation();
    }
    function addChainStep(){
      const m = document.getElementById('chainMethod').value.trim();
      const p = document.getElementById('chainPath').value.trim();
      const b = document.getElementById('chainBody').value;
      const rp = document.getElementById('chainRememberPath').value.trim();
      const rk = document.getElementById('chainRememberKey').value.trim();
      if (!m || !p){ notify('error', 'Provide method and path.'); return; }
      chainArr.push({ method: m, path: p, body: b, rememberPath: rp, rememberKey: rk });
      document.getElementById('chainPath').value = '';
      document.getElementById('chainBody').value = '';
      document.getElementById('chainRememberPath').value = '';
      document.getElementById('chainRememberKey').value = '';
      renderChain();
    }
    function clearChain(){ chainArr.splice(0, chainArr.length); renderChain(); }

    // Assertions UI
    const assertionsArr = [];
    function renderAssertions(){
      const ul = document.getElementById('assertList');
      const hidden = document.getElementById('assertions');
      if (!ul || !hidden) return;
      ul.innerHTML = '';
      assertionsArr.forEach((l, i) => {
        const li = document.createElement('li');
        li.textContent = l;
        li.style.cursor = 'pointer';
        li.title = 'Click to remove';
        li.onclick = () => { assertionsArr.splice(i,1); renderAssertions(); };
        ul.appendChild(li);
      });
      hidden.value = assertionsArr.join('\n');
      scheduleQualityEvaluation();
    }
    function addAssertion(){
      const type = (document.getElementById('assertType').value||'').trim();
      const a = (document.getElementById('assertA').value||'').trim();
      const b = (document.getElementById('assertB').value||'').trim();
      if(!type || !a){ notify('error', 'Please provide type and path/header.'); return; }
      const line = [type,a,b].join('|');
      assertionsArr.push(line);
      document.getElementById('assertA').value = '';
      document.getElementById('assertB').value = '';
      renderAssertions();
    }
    function addAssertionLine(type, a, b){
      const line = [type, a||'', b||''].join('|');
      assertionsArr.push(line);
      renderAssertions();
    }
    function addAllSuggestionChips(){
      const cont = document.getElementById('assertSuggestions');
      if (!cont) return;
      const chips = cont.querySelectorAll('.chip');
      chips.forEach(ch => {
        // Parse back from chip label. We encoded as `${typePrefix}: ${a} = ${b}` or `${typePrefix}: ${a}`
        const txt = ch.textContent || '';
        const firstColon = txt.indexOf(':');
        if (firstColon < 0) return;
        const typePrefix = txt.slice(0, firstColon).trim().toUpperCase();
        let type = 'JSONPATH_EQUALS';
        if (typePrefix === 'HEADER') type = 'HEADER_EQUALS';
        else if (typePrefix === 'JSONPATH') type = 'JSONPATH_EQUALS';
        const rest = txt.slice(firstColon+1).trim();
        const eqIdx = rest.indexOf('=');
        const a = (eqIdx >= 0 ? rest.slice(0, eqIdx).trim() : rest.trim());
        const b = (eqIdx >= 0 ? rest.slice(eqIdx+1).trim() : '');
        addAssertionLine(type, a, b);
      });
    }
    function clearAssertions(){
      assertionsArr.splice(0, assertionsArr.length);
      renderAssertions();
    }

    function initTokenSelector(){
      const select = document.getElementById('tokenEnvVarSelect');
      const customInput = document.getElementById('tokenEnvVarCustom');
      if (!select || !customInput) return;
      const sync = () => {
        const val = select.value;
        if (val === '__custom__') {
          customInput.style.display = 'block';
          customInput.name = 'tokenEnvVar';
          select.name = 'tokenEnvVarSelect';
        } else if (val === '') {
          customInput.style.display = 'none';
          customInput.value = '';
          customInput.name = 'tokenEnvVarCustom';
          select.name = 'tokenEnvVar';
        } else {
          customInput.style.display = 'none';
          customInput.value = '';
          customInput.name = 'tokenEnvVarCustom';
          select.name = 'tokenEnvVar';
        }
      };
      select.addEventListener('change', sync);
      sync();
    }

    function initStepper(){
      stepSections = Array.from(document.querySelectorAll('.editor-group'));
      const nav = document.getElementById('stepNav');
      if (!nav || stepSections.length === 0) return;
      nav.innerHTML = '';
      stepSections.forEach((sec, idx) => {
        const title = sec.dataset.stepTitle || `Step ${idx + 1}`;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = `${idx + 1}. ${title}`;
        btn.addEventListener('click', () => goToStep(idx));
        nav.appendChild(btn);
      });
      stepButtons = Array.from(nav.querySelectorAll('button'));
      document.querySelectorAll('[data-step-next]').forEach(btn => btn.addEventListener('click', () => goToStep(currentStep + 1)));
      document.querySelectorAll('[data-step-prev]').forEach(btn => btn.addEventListener('click', () => goToStep(currentStep - 1)));
      goToStep(0);
    }

    function goToStep(idx){
      if (!stepSections.length) return;
      if (idx < 0) idx = 0;
      if (idx >= stepSections.length) idx = stepSections.length - 1;
      currentStep = idx;
      stepSections.forEach((sec, i) => {
        const active = i === idx;
        sec.classList.toggle('active', active);
        sec.style.display = active ? 'block' : 'none';
      });
      if (stepButtons.length){
        stepButtons.forEach((btn, i) => btn.classList.toggle('active', i === idx));
      }
      updateStepActions();
      const activeSection = stepSections[idx];
      if (activeSection) activeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function updateStepActions(){
      document.querySelectorAll('[data-step-prev]').forEach(btn => {
        const hidden = currentStep === 0;
        btn.style.visibility = hidden ? 'hidden' : 'visible';
        btn.disabled = hidden;
      });
      document.querySelectorAll('[data-step-next]').forEach(btn => {
        const hidden = currentStep === stepSections.length - 1;
        btn.style.visibility = hidden ? 'hidden' : 'visible';
        btn.disabled = hidden;
      });
    }

    function setAssertionsJsonEnabled(enabled){
      const note = document.getElementById('assertJsonNote');
      const typeSel = document.getElementById('assertType');
      if (note) note.style.display = enabled ? 'none' : 'block';
      if (!typeSel) return;
      // Disable only JSONPATH_* options when not enabled
      for (const opt of typeSel.options){
        const isJsonPath = opt.value.startsWith('JSONPATH_');
        opt.disabled = !enabled && isJsonPath;
      }
    }

    function suggestFromSample(){
      const ta = document.getElementById('assertSample');
      const cont = document.getElementById('assertSuggestions');
      const note = document.getElementById('assertSuggestNote');
      if (!ta) return;
      let obj;
      try{ obj = JSON.parse(ta.value); }
      catch(e){ notify('error', 'Sample is not valid JSON.'); return; }
      const chips = [];
      function walk(node, prefix, depth){
        if (depth > 2) return; // keep it simple
        if (node === null) { chips.push(['JSONPATH_EXISTS', prefix, '']); return; }
        if (Array.isArray(node)) {
          chips.push(['JSONPATH_EXISTS', prefix, '']);
          return;
        }
        if (typeof node === 'object'){
          for (const k of Object.keys(node)){
            walk(node[k], prefix ? prefix + '.' + k : '$.'+k, depth+1);
          }
        } else {
          // primitive: suggest equals if short, else contains
          const val = String(node);
          if (val.length <= 30) chips.push(['JSONPATH_EQUALS', prefix, val]);
          else chips.push(['JSONPATH_CONTAINS', prefix, val.slice(0, 15)]);
        }
      }
      walk(obj, '$', 0);
      if (cont) {
        cont.innerHTML = '';
        chips.slice(0, 12).forEach(([t,a,b]) => {
          const chip = document.createElement('button');
          chip.type = 'button';
          chip.className = 'chip';
          chip.textContent = `${t.split('_')[0]}: ${a}${b? ' = '+b:''}`;
          chip.onclick = () => addAssertionLine(t,a,b);
          cont.appendChild(chip);
        });
      }
      if (note) note.textContent = 'Suggestions from sample JSON — click to add.';
      setAssertionsJsonEnabled(true);
    }

    function sampleToPositive(){
      const ta = document.getElementById('assertSample');
      const dst = document.getElementById('expJsonPositive');
      if (!ta || !dst) return;
      try{ const obj = JSON.parse(ta.value); dst.value = JSON.stringify(obj, null, 2); }
      catch(e){ notify('error', 'Sample is not valid JSON.'); }
    }
    function sampleToAll(){
      const ta = document.getElementById('assertSample');
      if (!ta) return;
      let pretty;
      try{ const obj = JSON.parse(ta.value); pretty = JSON.stringify(obj, null, 2); }
      catch(e){ notify('error', 'Sample is not valid JSON.'); return; }
      ['expJsonPositive','expJsonIdem','expJsonAuth','expJsonNegative'].forEach(id => {
        const el = document.getElementById(id); if (el) el.value = pretty;
      });
    }
    function escapeHtml(s){
      return s.replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
    }
    async function downloadTemplate(fmt){
      const form = document.querySelector('form');
      const curl = form.querySelector('textarea[name="curl"]').value;
      const manualJsonEl = form.querySelector('textarea[name="manualJson"]');
      const manualJson = manualJsonEl ? manualJsonEl.value : '';
      const datasetNameEl = form.querySelector('input[name="datasetName"]');
      const datasetName = datasetNameEl ? datasetNameEl.value : '';
      const body = new URLSearchParams({curl, manualJson, datasetName});
      const url = fmt === 'xlsx' ? '/api/template/dataset.xlsx' : '/api/template/dataset.csv';
      const res = await fetch(url, { method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded'}, body });
      if(!res.ok){ notify('error', 'Failed to generate template.'); return; }
      const blob = await res.blob();
      const a = document.createElement('a');
      const dlName = (datasetName || 'dataset') + '.' + (fmt === 'xlsx' ? 'xlsx' : 'csv');
      a.href = URL.createObjectURL(blob);
      a.download = dlName;
      document.body.appendChild(a);
      a.click();
      URL.revokeObjectURL(a.href);
      a.remove();
    }
    // ---- Expected JSON helpers ----
    function readSampleJson(){
      const ta = document.getElementById('assertSample');
      if (!ta) return null;
      const raw = ta.value || '';
      if (!raw.trim()) return null;
      try { return JSON.parse(raw); } catch(e){ notify('error', 'Sample Response JSON is not valid JSON.'); return null; }
    }
    function useSampleFor(id){
      const obj = readSampleJson();
      if (!obj) return;
      const el = document.getElementById(id);
      if (el){ el.value = JSON.stringify(obj, null, 2); }
    }
    function formatJsonTextarea(id){
      const el = document.getElementById(id);
      if (!el) return;
      const raw = el.value || '';
      if (!raw.trim()) { return; }
      try{ const o = JSON.parse(raw); el.value = JSON.stringify(o, null, 2); }
      catch(e){ notify('error', 'Not valid JSON.'); }
    }
    function copyJsonToAll(fromId){
      const src = document.getElementById(fromId);
      if (!src || !src.value.trim()) { notify('warn', 'Nothing to copy.'); return; }
      ['expJsonIdem','expJsonAuth','expJsonNegative'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = src.value;
      });
    }

    function formatAllExpected(){
      ['expJsonPositive','expJsonIdem','expJsonAuth','expJsonNegative'].forEach(id => formatJsonTextarea(id));
    }

    const focusableSelectors = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([type="hidden"]):not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const modalFocusState = new WeakMap();

    function getFocusableElements(container){
      return Array.from(container.querySelectorAll(focusableSelectors)).filter(el => {
        return el.offsetParent !== null && !el.hasAttribute('hidden');
      });
    }

    function showModal(modal, options = {}){
      if (!modal) return;
      if (!modal.dataset.modalBound){
        modal.addEventListener('click', event => {
          if (event.target === modal) hideModal(modal);
        });
        modal.dataset.modalBound = '1';
      }
      modal.classList.add('is-visible');
      modal.removeAttribute('hidden');
      modal.setAttribute('aria-hidden', 'false');
      const state = {
        previousFocus: document.activeElement,
        handleKeydown: null
      };
      state.handleKeydown = event => {
        if (event.key === 'Escape'){
          event.preventDefault();
          hideModal(modal);
          return;
        }
        if (event.key !== 'Tab') return;
        const focusables = getFocusableElements(modal);
        if (!focusables.length){
          event.preventDefault();
          return;
        }
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        const active = document.activeElement;
        if (event.shiftKey){
          if (active === first || !modal.contains(active)){
            event.preventDefault();
            last.focus();
          }
        } else {
          if (active === last){
            event.preventDefault();
            first.focus();
          }
        }
      };
      modal.addEventListener('keydown', state.handleKeydown);
      modalFocusState.set(modal, state);
      const explicit = options.initialFocus ? modal.querySelector(options.initialFocus) : null;
      const target = explicit || getFocusableElements(modal)[0];
      if (target) window.setTimeout(() => target.focus(), 0);
    }

    function hideModal(modal){
      if (!modal) return;
      modal.classList.remove('is-visible');
      modal.setAttribute('aria-hidden', 'true');
      modal.setAttribute('hidden', '');
      const state = modalFocusState.get(modal);
      if (state){
        modal.removeEventListener('keydown', state.handleKeydown);
        const prev = state.previousFocus;
        if (prev && document.contains(prev)) prev.focus();
        modalFocusState.delete(modal);
      }
    }

    // ----- Required fields picker -----
    function readRequestPayload(){
      const manualBox = document.querySelector('textarea[name="manualJson"]');
      if (manualBox){
        const val = manualBox.value || '';
        if (val.trim()){
          try {
            return JSON.parse(val);
          } catch (err){
            console.warn('Manual JSON payload is not valid JSON for picker:', err);
          }
        }
      }
      const cached = document.querySelector('textarea[name="manualJson"]')?.dataset.parsedFromCurl;
      if (cached){
        try { return JSON.parse(cached); } catch (err) { /* ignore */ }
      }
      return readPayloadFromCurl();
    }

    function readPayloadFromCurl(){
      const curlBox = document.querySelector('textarea[name="curl"]');
      if (!curlBox) return null;
      const raw = curlBox.value || '';
      if (!raw.trim()) return null;
      const regex = /(?:-d|--data(?:-raw)?|--data-binary)\s+\$?(['"])([\s\S]*?)\1/g;
      let match;
      let payload = null;
      while ((match = regex.exec(raw)) !== null){
        payload = match[2];
      }
      if (!payload) return null;
      // Unescape common shell escaped quotes
      payload = payload.replace(/\\"/g, '"').replace(/\\n/g, '\n');
      payload = payload.replace(/\\'/g, "'");
      try {
        const parsed = JSON.parse(payload);
        const manualBox = document.querySelector('textarea[name="manualJson"]');
        if (manualBox) manualBox.dataset.parsedFromCurl = JSON.stringify(parsed);
        return parsed;
      } catch (err){
        console.warn('cURL body is not valid JSON for picker:', err);
        return null;
      }
    }

    function openRequiredPicker(){
      const modal = document.getElementById('reqModal');
      const list = document.getElementById('reqList');
      if (!modal || !list) return;
      list.innerHTML = '';
      const seen = new Set();
      const payloadObj = readRequestPayload();
      const payloadPaths = payloadObj ? collectLeafPathsFromObj(payloadObj, '') : [];
      payloadPaths.forEach(p => seen.add(p));
      let samplePaths = [];
      const ta = document.getElementById('assertSample');
      try{
        const obj = ta && ta.value ? JSON.parse(ta.value) : null;
        if (obj){
          samplePaths = collectLeafPathsFromObj(obj, '').filter(p => !seen.has(p));
        }
      }catch(e){ samplePaths = []; }

      if (!payloadPaths.length && !samplePaths.length){
        const div = document.createElement('div');
        div.className = 'hint';
        div.textContent = 'No request payload or sample JSON fields found.';
        list.appendChild(div);
      } else {
        const current = new Set((document.getElementById('requiredFields').value||'').split('\n').map(s=>s.trim()).filter(Boolean));
        const appendGroup = (title, arr) => {
          if (!arr.length) return;
          const header = document.createElement('div');
          header.className = 'picker-group';
          header.textContent = title;
          list.appendChild(header);
          arr.forEach(p => {
            const row = document.createElement('label');
            row.className = 'req-row';
            const text = document.createElement('span');
            text.textContent = p;
            const cb = document.createElement('input');
            cb.type = 'checkbox'; cb.value = p; cb.checked = current.has(p);
            row.appendChild(text);
            row.appendChild(cb);
            list.appendChild(row);
          });
        };
        appendGroup('Request payload', payloadPaths);
        appendGroup('Sample response', samplePaths);
      }
      showModal(modal, { initialFocus: '#reqFilter' });
    }
    function closeReqModal(){
      const modal = document.getElementById('reqModal');
      if (modal) hideModal(modal);
    }
    function reqSelectAll(val){
      const list = document.getElementById('reqList'); if (!list) return;
      list.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = !!val);
    }
    function applyRequiredFromPicker(){
      const list = document.getElementById('reqList'); const ta = document.getElementById('requiredFields');
      if (!list || !ta) return;
      const vals = []; list.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => vals.push(cb.value));
      ta.value = vals.join('\n');
      closeReqModal();
    }
    function clearRequired(){ const ta = document.getElementById('requiredFields'); if (ta) ta.value=''; scheduleQualityEvaluation(); }
    function reqApplyFilter(){
      const qEl = document.getElementById('reqFilter');
      const list = document.getElementById('reqList');
      if (!qEl || !list) return;
      const q = (qEl.value||'').toLowerCase().trim();
      const items = list.querySelectorAll('label');
      items.forEach(lb => {
        const txt = (lb.textContent||'').toLowerCase();
        lb.style.display = !q || txt.includes(q) ? 'block' : 'none';
      });
    }
    function reqClearFilter(){ const qEl = document.getElementById('reqFilter'); if (qEl){ qEl.value=''; reqApplyFilter(); } }
    function collectLeafPathsFromObj(node, prefix){
      const out = [];
      if (node && typeof node === 'object' && !Array.isArray(node)){
        for (const k of Object.keys(node)){
          const child = node[k];
          const path = prefix ? prefix + '.' + k : k;
          if (child && typeof child === 'object' && !Array.isArray(child)){
            out.push(...collectLeafPathsFromObj(child, path));
          } else if (Array.isArray(child)){
            if (child.length && typeof child[0] === 'object' && child[0] !== null){
              out.push(...collectLeafPathsFromObj(child[0], path + '[0]'));
            } else {
              out.push(path);
            }
          } else {
            out.push(path);
          }
        }
      } else if (Array.isArray(node)){
        // top-level array
        if (node.length && typeof node[0] === 'object' && node[0] !== null){
          out.push(...collectLeafPathsFromObj(node[0], (prefix||'') + '[0]'));
        } else if (prefix){ out.push(prefix); }
      } else if (prefix){ out.push(prefix); }
      return out;
    }
    // ----- Length rules picker -----
    function openLengthPicker(){
      const modal = document.getElementById('lenModal');
      const list = document.getElementById('lenList');
      if (!modal || !list) return;
      list.innerHTML = '';
      const payloadObj = readRequestPayload();
      const payloadItems = payloadObj ? collectLengthCandidates(payloadObj) : [];
      const seen = new Set(payloadItems.map(i => i.path));
      let sampleItems = [];
      const ta = document.getElementById('assertSample');
      try {
        const obj = ta && ta.value ? JSON.parse(ta.value) : null;
        if (obj){
          sampleItems = collectLengthCandidates(obj).filter(item => !seen.has(item.path));
        }
      } catch(e){ sampleItems = []; }

      if (!payloadItems.length && !sampleItems.length){
        const div = document.createElement('div');
        div.className = 'hint';
        div.textContent = 'No request payload or sample JSON string/array fields found.';
        list.appendChild(div);
      } else {
        const current = new Map();
        (document.getElementById('lengthRules').value||'').split('\n').map(s=>s.trim()).filter(Boolean).forEach(line => {
          const [p,min,max] = line.split('|');
          current.set(p, {min: (min||''), max: (max||'')});
        });
        const appendGroup = (title, arr) => {
          if (!arr.length) return;
          const header = document.createElement('div');
          header.className = 'picker-group';
          header.textContent = title;
          list.appendChild(header);
          arr.forEach(({path,type,len}) => {
            const row = document.createElement('div');
            row.className = 'len-row';
            const label = document.createElement('label');
            label.dataset.path = path;
            const text = document.createElement('span');
            text.textContent = `${path} (${type}${len!=null? ', len='+len:''})`;
            const cb = document.createElement('input'); cb.type='checkbox'; cb.value=path; cb.checked = current.has(path);
            label.appendChild(text);
            label.appendChild(cb);
            const min = document.createElement('input'); min.type='number'; min.min='0'; min.placeholder='min';
            const max = document.createElement('input'); max.type='number'; max.min='0'; max.placeholder='max';
            const prev = current.get(path);
            if (prev){ min.value = prev.min; max.value = prev.max; }
            row.appendChild(label); row.appendChild(min); row.appendChild(max);
            // tag inputs for later read
            row.dataset.path = path;
            row.dataset.type = type;
            list.appendChild(row);
          });
        };
        appendGroup('Request payload', payloadItems);
        appendGroup('Sample response', sampleItems);
      }
      showModal(modal, { initialFocus: '#lenFilter' });
    }
    function closeLenModal(){
      const modal = document.getElementById('lenModal');
      if (modal) hideModal(modal);
    }
    function lenSelectAll(val){ const list = document.getElementById('lenList'); if (!list) return; list.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = !!val); }
    function applyLengthFromPicker(){
      const list = document.getElementById('lenList'); const ta = document.getElementById('lengthRules');
      if (!list || !ta) return;
      const lines = [];
      list.querySelectorAll('div').forEach(row => {
        const cb = row.querySelector('input[type="checkbox"]');
        const inputs = row.querySelectorAll('input[type="number"]');
        if (!cb) return;
        if (!cb.checked) return;
        const path = row.dataset.path;
        const min = (inputs[0] && inputs[0].value || '').trim();
        const max = (inputs[1] && inputs[1].value || '').trim();
        if (!min && !max) return; // ignore empty rules
        lines.push([path, min, max].join('|'));
      });
      ta.value = lines.join('\n');
      closeLenModal();
      scheduleQualityEvaluation();
    }
    function clearLengthRules(){ const ta = document.getElementById('lengthRules'); if (ta) ta.value=''; scheduleQualityEvaluation(); }
    function lenApplyFilter(){
      const qEl = document.getElementById('lenFilter'); const list = document.getElementById('lenList');
      if (!qEl || !list) return;
      const q = (qEl.value||'').toLowerCase().trim();
      const rows = list.querySelectorAll('div');
      rows.forEach(row => { const txt = (row.textContent||'').toLowerCase(); row.style.display = !q || txt.includes(q) ? 'flex' : 'none'; });
    }
    function lenClearFilter(){ const qEl = document.getElementById('lenFilter'); if (qEl){ qEl.value=''; lenApplyFilter(); } }
    function collectLengthCandidates(obj){
      const out = [];
      function walk(node, prefix){
        if (node === null || node === undefined) return;
        if (Array.isArray(node)){
          out.push({path: prefix, type: 'array', len: node.length});
          if (node.length && node[0] && typeof node[0] === 'object' && !Array.isArray(node[0])){
            walk(node[0], prefix + '[0]');
          }
          return;
        }
        if (typeof node === 'object'){
          for (const k of Object.keys(node)){
            const child = node[k];
            const p = prefix ? prefix + '.' + k : k;
            if (Array.isArray(child)){
              out.push({path: p, type: 'array', len: child.length});
              if (child.length && child[0] && typeof child[0] === 'object' && !Array.isArray(child[0])){
                walk(child[0], p + '[0]');
              }
            } else if (typeof child === 'string'){
              out.push({path: p, type: 'string', len: child.length});
            } else if (child && typeof child === 'object'){
              walk(child, p);
            }
          }
        } else if (typeof node === 'string'){
          out.push({path: prefix || '$', type: 'string', len: node.length});
        }
      }
      if (obj) walk(obj, '');
      return out;
    }
    // Standard footer year
    (function(){
      var el = document.getElementById('year');
      if (el) el.textContent = new Date().getFullYear();
    })();

    function bindDelegatedActions(){
      const clickHandlers = {
        'extract-from-curl': () => extractFromCurl(),
        'validate-curl': () => validateCurl(),
        'apply-preset': (_event, el) => {
          const preset = el.dataset.preset || '';
          if (preset) applyPreset(preset);
        },
        'add-assertion': () => addAssertion(),
        'add-all-suggestions': () => addAllSuggestionChips(),
        'clear-assertions': () => clearAssertions(),
        'suggest-from-sample': () => suggestFromSample(),
        'sample-to': (_event, el) => {
          if (el.dataset.target === 'all') sampleToAll();
          else sampleToPositive();
        },
        'use-sample': (_event, el) => {
          const id = el.dataset.target;
          if (id) useSampleFor(id);
        },
        'format-json': (_event, el) => {
          const id = el.dataset.target;
          if (id) formatJsonTextarea(id);
        },
        'copy-json-all': (_event, el) => {
          const id = el.dataset.target;
          if (id) copyJsonToAll(id);
        },
        'format-all-expected': () => formatAllExpected(),
        'open-required-picker': () => openRequiredPicker(),
        'clear-required': () => clearRequired(),
        'open-length-picker': () => openLengthPicker(),
        'clear-length': () => clearLengthRules(),
        'req-close': () => closeReqModal(),
        'req-clear-filter': () => reqClearFilter(),
        'req-select': (_event, el) => {
          const mode = el.dataset.select;
          reqSelectAll(mode === 'all');
        },
        'req-apply': () => applyRequiredFromPicker(),
        'len-close': () => closeLenModal(),
        'len-clear-filter': () => lenClearFilter(),
        'len-select': (_event, el) => {
          const mode = el.dataset.select;
          lenSelectAll(mode === 'all');
        },
        'len-apply': () => applyLengthFromPicker(),
        'add-chain-step': () => addChainStep(),
        'clear-chain': () => clearChain(),
        'download-template': (_event, el) => {
          const format = el.dataset.format;
          if (format) downloadTemplate(format);
        },
        'generate-xhr': (_event, el) => {
          generateXHR(el);
        }
      };

      document.body.addEventListener('click', event => {
        const el = event.target.closest('[data-action]');
        if (!el) return;
        const handler = clickHandlers[el.dataset.action];
        if (!handler) return;
        if (el.tagName === 'A') event.preventDefault();
        handler(event, el);
      });

      document.querySelectorAll('[data-action="req-filter"]').forEach(input => {
        input.addEventListener('input', () => reqApplyFilter());
      });
      document.querySelectorAll('[data-action="len-filter"]').forEach(input => {
        input.addEventListener('input', () => lenApplyFilter());
      });
      document.querySelectorAll('[data-action="dataset-change"]').forEach(input => {
        input.addEventListener('change', ev => showFileInfo(ev));
      });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', bindDelegatedActions);
    else bindDelegatedActions();
