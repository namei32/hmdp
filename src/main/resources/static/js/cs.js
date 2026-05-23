/**
 * 黑马点评智能客服 - 前端逻辑
 * Vanilla JS + SSE via fetch ReadableStream
 */
(function () {
    'use strict';

    /* ================================================================
       State
       ================================================================ */

    var state = {
        conversations: [],
        conversationsLoading: false,
        activeConvId: null,
        messages: [],
        isStreaming: false,
        currentStreamContent: '',
        sidebarOpen: true,
        errorMessage: ''
    };

    /* ================================================================
       DOM refs
       ================================================================ */

    var el = {};
    function cacheDom() {
        el.sidebar         = document.getElementById('sidebar');
        el.sidebarOverlay  = document.getElementById('sidebarOverlay');
        el.convList        = document.getElementById('convList');
        el.sidebarEmpty    = document.getElementById('sidebarEmpty');
        el.chatMessages    = document.getElementById('chatMessages');
        el.messagesContainer = document.getElementById('messagesContainer');
        el.welcomeScreen   = document.getElementById('welcomeScreen');
        el.chatInput       = document.getElementById('chatInput');
        el.btnSend         = document.getElementById('btnSend');
        el.errorBanner     = document.getElementById('errorBanner');
        el.errorBannerText = document.getElementById('errorBannerText');
    }

    /* ================================================================
       Helpers
       ================================================================ */

    function escHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;')
                  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function renderMarkdown(text) {
        if (!text) return '';
        var html = escHtml(text);
        // Bold & italic
        html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        // Inline code
        html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
        // Newlines to <br>
        html = html.replace(/\n/g, '<br>');
        return html;
    }

    function formatToolName(name) {
        var map = {
            query_shop: '商户查询',
            get_shop_detail: '商户详情',
            query_voucher: '优惠券查询',
            query_order: '订单查询',
            query_blog: '探店笔记'
        };
        return map[name] || name || '平台数据';
    }

    function summarizeToolResult(content) {
        var text = (content || '').trim();
        if (!text) return '查询完成';

        var firstLine = text.split(/\r?\n/).filter(function (line) {
            return line.trim().length > 0;
        })[0] || text;

        var shopMatch = firstLine.match(/找到\s*(\d+)\s*家/);
        if (shopMatch) return '找到 ' + shopMatch[1] + ' 家相关商户';

        var countMatch = firstLine.match(/找到\s*(\d+)/);
        if (countMatch) return '找到 ' + countMatch[1] + ' 条相关结果';

        return firstLine.length > 36 ? firstLine.substring(0, 36) + '...' : firstLine;
    }

    function normalizeErrorMessage(msg) {
        var text = msg || '请求处理出错';
        if (text.indexOf('reasoning_content') !== -1) {
            return '模型工具上下文同步失败，请重试一次。';
        }
        if (text.indexOf('missing API key') !== -1 || text.indexOf('not configured') !== -1) {
            return 'AI 服务暂未配置完成，请检查后端密钥配置。';
        }
        if (text.indexOf('timeout') !== -1 || text.indexOf('timed out') !== -1) {
            return 'AI 服务响应超时，请稍后再试。';
        }
        return text;
    }

    /* ================================================================
       Error banner
       ================================================================ */

    function showError(msg) {
        state.errorMessage = msg;
        el.errorBannerText.textContent = msg;
        el.errorBanner.style.display = '';
    }

    function hideError() {
        state.errorMessage = '';
        el.errorBanner.style.display = 'none';
    }

    /* ================================================================
       Sidebar
       ================================================================ */

    function isMobile() {
        return window.matchMedia('(max-width: 768px)').matches;
    }

    function openSidebar() {
        state.sidebarOpen = true;
        el.sidebar.classList.remove('collapsed');
        if (isMobile()) {
            el.sidebarOverlay.classList.add('visible');
        }
    }

    function closeSidebar() {
        state.sidebarOpen = false;
        el.sidebar.classList.add('collapsed');
        if (isMobile()) {
            el.sidebarOverlay.classList.remove('visible');
        }
    }

    function toggleSidebar() {
        if (state.sidebarOpen) {
            closeSidebar();
        } else {
            openSidebar();
        }
    }

    // Initialize sidebar state for mobile
    function initSidebarForMobile() {
        if (isMobile()) {
            closeSidebar();
        }
    }

    /* ================================================================
       Render: Conversation list
       ================================================================ */

    function renderConvSkeleton() {
        var html = '<div class="sidebar-skeleton">';
        for (var i = 0; i < 5; i++) {
            html += '<div class="sidebar-skeleton-item"></div>';
        }
        html += '</div>';
        el.convList.innerHTML = html;
    }

    function renderConversations() {
        if (state.conversationsLoading) {
            renderConvSkeleton();
            el.sidebarEmpty.style.display = 'none';
            return;
        }

        var list = state.conversations;
        if (list.length === 0) {
            el.convList.innerHTML = '';
            el.convList.style.display = 'none';
            el.sidebarEmpty.style.display = '';
            return;
        }

        el.convList.style.display = '';
        el.sidebarEmpty.style.display = 'none';

        el.convList.innerHTML = list.map(function (c) {
            var cls = c.id === state.activeConvId ? ' conv-item active' : 'conv-item';
            return '<div class="' + cls + '" data-conv-id="' + escHtml(c.id) + '">' +
                '<div class="conv-title">' + escHtml(c.title || '新对话') + '</div>' +
                '<div class="conv-meta">' + (c.messageCount || 0) + ' 条消息</div>' +
                '<button class="btn-delete-conv" data-delete="' + escHtml(c.id) + '" title="删除对话" aria-label="删除对话">' +
                '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">' +
                '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/>' +
                '<line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>' +
                '</button></div>';
        }).join('');

        // Delegate clicks
        el.convList.onclick = function (e) {
            var delBtn = e.target.closest('.btn-delete-conv');
            if (delBtn) {
                e.stopPropagation();
                var id = delBtn.getAttribute('data-delete');
                if (id) deleteConversation(id);
                return;
            }
            var item = e.target.closest('.conv-item');
            if (item) {
                var cid = item.getAttribute('data-conv-id');
                if (cid) switchConversation(cid);
            }
        };
    }

    /* ================================================================
       Render: Messages
       ================================================================ */

    function renderMessages() {
        var hasMessages = state.messages.length > 0;
        var showMessages = hasMessages || state.isStreaming;

        el.chatMessages.style.display = showMessages ? '' : 'none';
        el.welcomeScreen.style.display = showMessages ? 'none' : '';

        if (!showMessages) return;

        var html = '';

        for (var i = 0; i < state.messages.length; i++) {
            var m = state.messages[i];
            var roleClass = m.role === 'user' ? 'user' : 'assistant';

            if (m.type === 'tool_call') {
                html += '<div class="tool-event-row is-loading">' +
                    '<div class="tool-event-line"></div>' +
                    '<div class="tool-event-card">' +
                    '<span class="tool-event-icon">' +
                    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">' +
                    '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg></span>' +
                    '<span class="tool-event-title">正在查询 ' + escHtml(formatToolName(m.toolName)) + '</span>' +
                    '<span class="tool-event-meta">检索中</span>' +
                    '</div></div>';
                continue;
            } else if (m.type === 'tool_result') {
                var expanded = m.expanded !== false;
                html += '<div class="tool-event-row is-done">' +
                    '<div class="tool-event-line"></div>' +
                    '<div class="tool-result-card">' +
                    '<button type="button" class="tool-result-header" data-toggle="' + i + '" aria-expanded="' + expanded + '">' +
                    '<span class="tool-event-icon">' +
                    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">' +
                    '<path d="M20 6 9 17l-5-5"/></svg></span>' +
                    '<span class="tool-result-title">' + escHtml(formatToolName(m.toolName)) + '</span>' +
                    '<span class="tool-result-summary">' + escHtml(summarizeToolResult(m.content)) + '</span>' +
                    '<svg class="tool-result-expand' + (expanded ? ' expanded' : '') + '" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">' +
                    '<polyline points="6 9 12 15 18 9"/></svg></button>' +
                    '<div class="tool-result-body" style="display:' + (expanded ? 'block' : 'none') + '">' + escHtml(m.content || '') + '</div></div></div>';
                continue;
            }

            var avatarHtml = roleClass === 'assistant'
                ? '<div class="message-avatar assistant-avatar">AI</div>'
                : '<div class="message-avatar user-avatar">我</div>';
            var toneClass = m.tone === 'error' ? ' error' : '';
            var bubbleHtml = '<div class="message-text">' + renderMarkdown(m.content || '') + '</div>';

            html += '<div class="message-row ' + roleClass + '">' +
                (roleClass === 'assistant' ? avatarHtml : '') +
                '<div class="message-bubble ' + roleClass + toneClass + '">' + bubbleHtml + '</div>' +
                (roleClass === 'user' ? avatarHtml : '') +
                '</div>';
        }

        // Typing indicator
        if (state.isStreaming) {
            html += '<div class="message-row assistant">' +
                '<div class="message-avatar assistant-avatar">AI</div>' +
                '<div class="message-bubble assistant typing-bubble">' +
                '<span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span>' +
                '</div></div>';
        }

        el.messagesContainer.innerHTML = html;

        // Tool result toggle
        var headers = el.messagesContainer.querySelectorAll('.tool-result-header');
        for (var h = 0; h < headers.length; h++) {
            headers[h].onclick = (function (idx) {
                return function () {
                    if (state.messages[idx]) {
                        state.messages[idx].expanded = state.messages[idx].expanded === false ? true : false;
                        renderMessages();
                        // Restore scroll position
                        scrollToBottom();
                    }
                };
            })(parseInt(headers[h].getAttribute('data-toggle')));
        }

        scrollToBottom();
    }

    function scrollToBottom() {
        requestAnimationFrame(function () {
            if (el.chatMessages) {
                el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
            }
        });
    }

    /* ================================================================
       API
       ================================================================ */

    function apiFetch(url, opts) {
        return fetch(url, opts)
            .then(function (r) {
                if (!r.ok) throw new Error('请求失败 (' + r.status + ')');
                var ct = r.headers.get('content-type') || '';
                return ct.indexOf('application/json') !== -1 ? r.json() : r.text();
            })
            .catch(function (e) {
                if (e.name === 'TypeError') {
                    throw new Error('网络连接失败，请检查网络');
                }
                throw e;
            });
    }

    /* ================================================================
       Conversations CRUD
       ================================================================ */

    function loadConversations() {
        state.conversationsLoading = true;
        renderConversations();

        apiFetch('/api/cs/conversations')
            .then(function (result) {
                state.conversationsLoading = false;
                if (result && result.success) {
                    state.conversations = result.data || [];
                }
                renderConversations();
            })
            .catch(function (e) {
                state.conversationsLoading = false;
                renderConversations();
                showError('加载对话列表失败: ' + e.message);
            });
    }

    function switchConversation(id) {
        state.activeConvId = id;
        state.messages = [];
        state.isStreaming = false;
        renderMessages();
        renderConversations();

        apiFetch('/api/cs/conversation/' + id)
            .then(function (result) {
                if (result && result.success && result.data && result.data.messages) {
                    state.messages = result.data.messages.filter(function (m) {
                        return m.role !== 'tool';
                    }).map(function (m) {
                        return {
                            role: m.role,
                            type: 'text',
                            content: m.content || '',
                        };
                    });
                }
                renderMessages();
                renderConversations();
                scrollToBottom();
            })
            .catch(function (e) {
                showError('加载对话失败: ' + e.message);
            });

        // On mobile, close sidebar after selecting a conversation
        if (isMobile()) {
            closeSidebar();
        }
    }

    function deleteConversation(id) {
        apiFetch('/api/cs/conversation/' + id, { method: 'DELETE' })
            .then(function () {
                if (state.activeConvId === id) {
                    state.activeConvId = null;
                    state.messages = [];
                    renderMessages();
                }
                loadConversations();
            })
            .catch(function (e) {
                showError('删除对话失败: ' + e.message);
            });
    }

    function newChat() {
        state.activeConvId = null;
        state.messages = [];
        state.currentStreamContent = '';
        state.isStreaming = false;
        hideError();
        renderMessages();
        renderConversations();
        el.chatInput.focus();

        if (isMobile()) {
            closeSidebar();
        }
    }

    /* ================================================================
       Chat / SSE
       ================================================================ */

    var streamingMsgIndex = -1;

    function sendMessage(text) {
        var msg = (text || el.chatInput.value).trim();
        if (!msg || state.isStreaming) return;

        hideError();
        el.chatInput.value = '';
        updateSendButton();
        state.isStreaming = true;
        state.currentStreamContent = '';
        streamingMsgIndex = -1;

        // Add user message
        state.messages.push({ role: 'user', type: 'text', content: msg });
        renderMessages();

        // On mobile, close sidebar after sending
        if (isMobile()) {
            closeSidebar();
        }

        var body = JSON.stringify({
            message: msg,
            conversationId: state.activeConvId || null
        });

        fetch('/api/cs/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body
        }).then(function (resp) {
            if (!resp.ok) {
                return resp.text().then(function (t) {
                    throw new Error(t || 'HTTP ' + resp.status);
                });
            }
            return readSseStream(resp);
        }).catch(function (e) {
            var friendly = normalizeErrorMessage(e.message);
            state.messages.push({
                role: 'assistant',
                type: 'text',
                content: '抱歉，请求失败：' + friendly,
                tone: 'error'
            });
            finishStreaming();
            showError(friendly);
        });
    }

    function readSseStream(resp) {
        var reader = resp.body.getReader();
        var decoder = new TextDecoder('utf-8');
        var buffer = '';

        function pump() {
            return reader.read().then(function (result) {
                if (result.done) {
                    if (buffer.trim()) {
                        processSseBuffer(buffer);
                    }
                    finishStreaming();
                    return;
                }

                buffer += decoder.decode(result.value, { stream: true });

                // Split on double-newline (SSE event boundary)
                var parts = buffer.split('\n\n');
                // Keep the last (potentially incomplete) part in the buffer
                buffer = parts.pop() || '';

                for (var i = 0; i < parts.length; i++) {
                    processSseBlock(parts[i]);
                }

                return pump();
            }).catch(function (e) {
                var friendly = normalizeErrorMessage(e.message);
                state.messages.push({
                    role: 'assistant',
                    type: 'text',
                    content: '连接中断：' + friendly,
                    tone: 'error'
                });
                finishStreaming();
                showError('流式连接中断');
            });
        }

        return pump();
    }

    function processSseBlock(block) {
        if (!block || !block.trim()) return;

        var lines = block.split('\n');
        var eventName = 'message';
        var eventData = '';

        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (line.indexOf('event:') === 0) {
                eventName = line.substring(6).trim();
            } else if (line.indexOf('data:') === 0) {
                eventData = line.substring(5).trim();
            }
        }

        if (!eventData) return;

        try {
            var data = JSON.parse(eventData);
            handleSseEvent(eventName, data);
        } catch (e) {
            // Non-JSON data — treat as text content
            if (eventName === 'text' || eventName === 'message') {
                appendStreamingChunk(eventData);
            }
        }
    }

    function processSseBuffer(buffer) {
        var lines = buffer.split('\n');
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (line.indexOf('data:') === 0) {
                var dataStr = line.substring(5).trim();
                if (!dataStr) continue;
                try {
                    var parsed = JSON.parse(dataStr);
                    if (parsed.content) {
                        appendStreamingChunk(parsed.content);
                    }
                } catch (e) {
                    appendStreamingChunk(dataStr);
                }
            }
        }
    }

    function handleSseEvent(eventName, data) {
        switch (eventName) {
            case 'text':
            case 'message':
                if (data.content) {
                    appendStreamingChunk(data.content);
                }
                break;

            case 'tool_call':
                break;

            case 'tool_result':
                break;

            case 'meta':
                if (data.conversationId && !state.activeConvId) {
                    state.activeConvId = data.conversationId;
                }
                break;

            case 'error':
                var friendly = normalizeErrorMessage(data.message);
                state.messages.push({
                    role: 'assistant',
                    type: 'text',
                    content: friendly,
                    tone: 'error'
                });
                finishStreaming();
                showError(friendly);
                break;

            case 'done':
                // Streaming message will be finalized in finishStreaming
                break;

            default:
                // Unknown event — try to extract content
                if (data.content) {
                    appendStreamingChunk(data.content);
                }
                break;
        }
    }

    function appendStreamingChunk(chunk) {
        if (typeof chunk !== 'string' || !chunk) return;

        if (streamingMsgIndex < 0) {
            state.messages.push({
                role: 'assistant',
                type: 'text',
                content: ''
            });
            streamingMsgIndex = state.messages.length - 1;
        }

        state.messages[streamingMsgIndex].content += chunk;
        state.currentStreamContent += chunk;
        renderMessages();
    }

    function finishStreaming() {
        state.isStreaming = false;
        streamingMsgIndex = -1;
        state.currentStreamContent = '';
        renderMessages();
        scrollToBottom();
        updateSendButton();
        // Refresh conversation list to pick up new/updated conversations
        loadConversations();
    }

    /* ================================================================
       Input handling
       ================================================================ */

    function updateSendButton() {
        var hasText = el.chatInput.value.trim().length > 0;
        el.btnSend.disabled = !hasText || state.isStreaming;
    }

    /* ================================================================
       Init
       ================================================================ */

    function bindEvents() {
        // Input
        el.chatInput.addEventListener('input', updateSendButton);
        el.chatInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
                e.preventDefault();
                sendMessage();
            }
        });
        el.btnSend.addEventListener('click', function () {
            sendMessage();
        });

        // New chat buttons
        document.getElementById('btnNewChat').addEventListener('click', newChat);
        document.getElementById('btnMobileNew').addEventListener('click', newChat);

        // Sidebar toggle
        document.getElementById('btnToggleSidebar').addEventListener('click', toggleSidebar);
        el.sidebarOverlay.addEventListener('click', closeSidebar);

        // Error banner close
        document.getElementById('errorBannerClose').addEventListener('click', hideError);

        // Suggestion chips
        var chips = document.querySelectorAll('#suggestions .chip');
        for (var i = 0; i < chips.length; i++) {
            chips[i].addEventListener('click', function () {
                // Extract text without the icon
                var text = this.textContent.replace(/[\u{1F300}-\u{1FAFF}]/gu, '').trim();
                sendMessage(text);
            });
        }

        // Keyboard shortcut: Ctrl+K to toggle sidebar
        document.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                toggleSidebar();
            }
        });

        // Handle window resize for responsive behavior
        var resizeTimer;
        window.addEventListener('resize', function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                if (!isMobile() && !state.sidebarOpen) {
                    openSidebar();
                }
                if (isMobile() && state.sidebarOpen) {
                    closeSidebar();
                }
            }, 150);
        });
    }

    function init() {
        cacheDom();
        bindEvents();
        initSidebarForMobile();
        loadConversations();
        el.chatInput.focus();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
