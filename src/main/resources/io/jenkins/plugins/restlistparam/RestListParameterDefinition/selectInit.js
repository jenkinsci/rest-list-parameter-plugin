jQuery3.noConflict();
jQuery3(document).ready(function () {
  jQuery3("[data-select2-id]").each(function () {
    var $sel = jQuery3(this);
    var options = { theme: 'bootstrap4' };
    // When the parameter does not allow an empty value, use a placeholder so the
    // user is prompted to pick a real option. When empty values are allowed, the
    // first <option value=""> rendered by index.jelly must remain a real
    // selectable entry — select2 would otherwise consume it as the placeholder
    // slot and hide it from the dropdown.
    if ($sel.data('allow-empty-value') !== true) {
      options.placeholder = "Select an option";
    }
    $sel.select2(options);
  });
});
