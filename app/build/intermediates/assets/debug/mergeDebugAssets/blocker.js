(function() {
  'use strict';

  var bridge = typeof Android !== 'undefined' ? Android : null;
  var CONFIG = {
    confidenceThreshold: 85,
    mutationThrottleMs: 150,
    inactiveTimeoutMs: 5000
  };
  var safeNodes = new WeakSet();
  var blockedElements = new Map();
  var debugLog = [];
  var pauseScanning = false;
  var inactiveSince = Date.now();
  var blockedCount = 0;
  var feedObserver = null;

  function isInsideComment(node) {
    if (safeNodes.has(node)) return true;
    var el = node;
    while (el && el !== document.body) {
      if (el.getAttribute) {
        if (el.getAttribute('role') === 'article') {
          safeNodes.add(node);
          return true;
        }
        var testId = el.getAttribute('data-testid');
        if (testId && /comment|UFI|reply/i.test(testId)) {
          safeNodes.add(node);
          return true;
        }
        if (el.getAttribute('contenteditable') === 'true') {
          safeNodes.add(node);
          return true;
        }
        if (el.getAttribute('role') === 'textbox') {
          safeNodes.add(node);
          return true;
        }
      }
      el = el.parentElement;
    }

    var url = window.location.href;
    if (/comment_id=|reply_comment_id=|\/comments\//.test(url)) {
      safeNodes.add(node);
      return true;
    }
    return false;
  }

  function checkUrlLayer() {
    var url = window.location.href;
    if (url.indexOf('/reels/') !== -1 || url.indexOf('/reel/') !== -1 || /[?&]tab=reels(&|$)/.test(url)) {
      return 40;
    }
    return 0;
  }

  function checkVisualLayer(container) {
    var video = container.querySelector('video');
    if (!video) return 0;
    var rect = container.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return 0;
    var aspectRatio = rect.width / rect.height;
    if (aspectRatio >= 0.4 && aspectRatio <= 0.65) {
      var hasEngagement = container.querySelector('[aria-label*="Like" i], [aria-label*="Share" i], [aria-label*="Comment" i]');
      if (hasEngagement) return 40;
      return 25;
    }
    return 0;
  }

  function checkTextLayer(container) {
    var text = container.textContent || '';
    if (/Reels/i.test(text)) return 20;
    var els = container.querySelectorAll('[data-testid]');
    for (var i = 0; i < els.length; i++) {
      if (/reel/i.test(els[i].getAttribute('data-testid'))) return 20;
    }
    return 0;
  }

  function calculateConfidence(container) {
    var score = checkUrlLayer();
    if (score < 40) score += checkVisualLayer(container);
    if (score < 80) score += checkTextLayer(container);

    var friendBadge = container.querySelector('[aria-label*="Friends" i], [data-testid*="friends" i]');
    if (friendBadge) score = Math.max(0, score - 10);

    var liveEl = container.querySelector('[aria-label*="Live" i], [data-testid*="live" i]');
    if (liveEl || window.location.href.indexOf('/live/') !== -1) return 0;

    return Math.min(100, score);
  }

  function isSponsored(container) {
    var sponsored = container.querySelector('[aria-label*="Sponsored" i], [data-testid*="sponsored" i]');
    if (sponsored) return true;
    var text = container.textContent || '';
    if (/Sponsored/i.test(text)) return true;
    return false;
  }

  function createReplacementCard(container) {
    var itemId = container.getAttribute('data-fb-blocker-id');
    if (!itemId) {
      itemId = 'item_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
      container.setAttribute('data-fb-blocker-id', itemId);
    }
    var whitelisted = localStorage.getItem('fb_blocker_whitelist_' + itemId);
    if (whitelisted && (Date.now() - parseInt(whitelisted, 10)) < 3600000) return false;

    var card = document.createElement('div');
    card.style.cssText = 'background:#1e2d45;border-radius:8px;padding:16px;margin:8px 0;text-align:center;color:#94a3b8;font-family:-apple-system,BlinkMacSystemFont,Roboto,sans-serif;';
    card.setAttribute('data-fb-blocker-card', 'true');

    var textEl = document.createElement('div');
    textEl.style.cssText = 'margin-bottom:8px;font-size:14px;';
    textEl.textContent = 'Short video hidden';
    card.appendChild(textEl);

    var btnContainer = document.createElement('div');
    btnContainer.style.cssText = 'display:flex;gap:8px;justify-content:center;';

    var showBtn = document.createElement('button');
    showBtn.textContent = 'Show Once';
    showBtn.style.cssText = 'background:#4F8EF7;color:white;border:none;border-radius:4px;padding:6px 12px;cursor:pointer;font-size:13px;';

    var disableBtn = document.createElement('button');
    disableBtn.textContent = 'Disable for 1h';
    disableBtn.style.cssText = 'background:#475569;color:#94a3b8;border:1px solid #64748b;border-radius:4px;padding:6px 12px;cursor:pointer;font-size:13px;';

    showBtn.onclick = function(e) {
      e.stopPropagation();
      localStorage.removeItem('fb_blocker_whitelist_' + itemId);
    };

    disableBtn.onclick = function(e) {
      e.stopPropagation();
      localStorage.setItem('fb_blocker_whitelist_' + itemId, Date.now().toString());
      container.innerHTML = '';
      var msg = document.createElement('div');
      msg.style.cssText = 'background:#1e2d45;border-radius:8px;padding:16px;margin:8px 0;text-align:center;color:#64748b;font-size:13px;';
      msg.textContent = 'Reels hidden for 1 hour';
      container.appendChild(msg);
    };

    btnContainer.appendChild(showBtn);
    btnContainer.appendChild(disableBtn);
    card.appendChild(btnContainer);

    var video = container.querySelector('video');
    if (video) {
      video.removeAttribute('src');
      video.innerHTML = '';
      video.style.display = 'none';
    }

    container.innerHTML = '';
    container.appendChild(card);
    return true;
  }

  function processFeedItem(entry) {
    var container = entry.target || entry;
    if (blockedElements.has(container)) return;
    if (isInsideComment(container)) return;
    if (isSponsored(container)) return;

    var itemId = container.getAttribute('data-fb-blocker-id');
    if (itemId) {
      var whitelisted = localStorage.getItem('fb_blocker_whitelist_' + itemId);
      if (whitelisted && (Date.now() - parseInt(whitelisted, 10)) < 3600000) return;
    }

    var confidence = calculateConfidence(container);
    if (confidence >= CONFIG.confidenceThreshold) {
      blockedElements.set(container, { timestamp: Date.now(), confidence: confidence, reason: 'reel' });

      if (createReplacementCard(container)) {
        blockedCount++;
        if (bridge) {
          try { bridge.onReelBlocked(blockedCount); } catch(e) {}
        }
        logDebug(container, confidence);
      }
    }
  }

  var intersectionObserver = new IntersectionObserver(function(entries) {
    if (pauseScanning) return;
    requestAnimationFrame(function() {
      var start = performance.now();
      for (var i = 0; i < entries.length; i++) {
        if (entries[i].isIntersecting) processFeedItem(entries[i]);
      }
      var elapsed = performance.now() - start;
      if (elapsed > 16 && bridge) {
        try { bridge.onPerformanceWarning(elapsed); } catch(e) {}
      }
    });
  }, { rootMargin: '200px 0px' });

  var mutationTimer = null;
  function onFeedMutation() {
    if (mutationTimer) return;
    mutationTimer = setTimeout(function() {
      mutationTimer = null;
      if (pauseScanning) return;
      var feed = document.querySelector('[role="feed"]');
      if (!feed) return;
      requestAnimationFrame(function() {
        var items = feed.querySelectorAll('[role="article"]');
        for (var i = 0; i < items.length; i++) {
          intersectionObserver.observe(items[i]);
        }
      });
    }, CONFIG.mutationThrottleMs);
  }

  function scanExistingFeed() {
    var feed = document.querySelector('[role="feed"]');
    if (!feed) return false;
    var items = feed.querySelectorAll('[role="article"]');
    for (var i = 0; i < items.length; i++) {
      intersectionObserver.observe(items[i]);
    }
    feedObserver = new MutationObserver(onFeedMutation);
    feedObserver.observe(feed, { childList: true, subtree: true });
    return true;
  }

  function init() {
    if (!scanExistingFeed()) {
      var pageObserver = new MutationObserver(function() {
        if (scanExistingFeed()) {
          pageObserver.disconnect();
        }
      });
      pageObserver.observe(document.body, { childList: true, subtree: true });
    }

    document.addEventListener('visibilitychange', function() {
      pauseScanning = document.visibilityState === 'hidden';
    });

    function resetInactivity() { inactiveSince = Date.now(); if (pauseScanning) pauseScanning = false; }
    document.addEventListener('scroll', resetInactivity);
    document.addEventListener('click', resetInactivity);
    setInterval(function() {
      if (Date.now() - inactiveSince > CONFIG.inactiveTimeoutMs) {
        pauseScanning = true;
      }
    }, 1000);
  }

  function logDebug(container, confidence) {
    debugLog.push({
      timestamp: new Date().toISOString(),
      confidence: confidence,
      html: (container.innerHTML || '').substring(0, 200)
    });
    if (debugLog.length > 10) debugLog.shift();
  }

  window.__fbBlockerDebug = {
    getLog: function() { return debugLog.slice(); },
    getStats: function() { return { blockedCount: blockedCount, paused: pauseScanning }; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
