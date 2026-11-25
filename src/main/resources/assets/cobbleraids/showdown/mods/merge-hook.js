// cobbleraids:merge-hook __FILE__
// Added by CobbleRaids (ShowdownModMerge): merges its own __EXPORT__ into this file's, beside Cobblemon's.
;(function () {
  const own = require('./__FILE__').__EXPORT__;
  const target = module.exports && module.exports.__EXPORT__;
  if (target && typeof target === 'object') Object.assign(target, own);
  else module.exports = Object.assign({}, module.exports, {__EXPORT__: own});
})();
