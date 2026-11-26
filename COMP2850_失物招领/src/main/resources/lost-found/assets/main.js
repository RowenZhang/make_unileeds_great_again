const search = document.querySelector('#q');
if (search) {
  const area = document.querySelector('#results');
  const status = document.querySelector('#search-status');
  let timer, active;
  let version = 0;
  search.addEventListener('input', () => {
    clearTimeout(timer);
    active?.abort();
    const current = ++version;
    timer = setTimeout(async () => {
      active = new AbortController();
      const url = new URL('/lost-found', location.origin);
      if (search.value.trim()) url.searchParams.set('q', search.value.trim());
      try {
        const response = await fetch('/lost-found/results' + url.search, {signal: active.signal});
        if (!response.ok) throw new Error();
        const markup = await response.text();
        if (current !== version) return;
        area.innerHTML = markup;
        history.replaceState(null, '', url.pathname + url.search);
        status.textContent = (area.querySelector('.count')?.textContent || 'Results') + ' found.';
      } catch (err) {
        if (err.name !== 'AbortError' && current === version) status.textContent = 'Live search unavailable. Use the Search button to try again.';
      }
    }, 200);
  });
}
const note = document.querySelector('#error-note');
if (note) {
  const field = note.querySelector('a');
  if (field) {
    const control = document.getElementById(field.hash.slice(1));
    control?.setAttribute('aria-invalid','true');
    control?.setAttribute('aria-describedby','error-note');
  }
  note.focus();
}
const picker = document.querySelector('#photo');
if (picker) {
  let old;
  picker.addEventListener('change', () => {
    const preview = document.querySelector('#preview');
    if (old) URL.revokeObjectURL(old);
    preview.hidden = true;
    const file = picker.files[0];
    if (!file) return;
    if (file.size <= 5 * 1024 * 1024 && ['image/jpeg', 'image/png'].includes(file.type)) {
      old = URL.createObjectURL(file);
      preview.src = old;
      preview.hidden = false;
    }
  });
}
