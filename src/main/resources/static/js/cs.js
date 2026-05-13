/**
 * 黑马点评智能客服 - 前端逻辑
 * Vanilla JS + native SSE via fetch ReadableStream
 */
(function () {
    'use strict';

    // --- State ---
    var state = {
        conversations: [],
        activeConvId: null,
        messages: [],
        isStreaming: false,
        currentStreamContent: '',
        sidebarCollapsed: false
    };

    // --- DOM refs ---
    var el = {};
    function cacheDom() {
        el.convList = document.getElementById('convList');
        el.sidebarEmpty = document.getElementById('sidebarEmpty');
        el.sidebar = document.querySelector('.sidebar');
        el.chatMessages = document.getElementById('chatMessages');
        el.messagesWrapper = document.getElementById('messagesWrapper');
        el.welcomeScreen = document.getElementById('welcomeScreen');
        el.chatInput = document.getElementById('chatInput');
        el.btnSend = document.getElementById('btnSend');
    }

    // --- Markdown ---
    function renderMarkdown(text) {
        if (!text) return '';
        return text
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.+?)\*/g, '<em>$1</em>')
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\n/g, '<br>');
    }

    function escHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // --- Render ---
    function renderConversations() {
        var list = state.conversations;
        if (list.length === 0) {
            el.convList.innerHTML = '';
            el.convList.style.display = 'none';
            el.sidebarEmpty.style.display = '';
            return;
        }
        el.convList.style.display = '';
        el.sidebarEmpty.style.display = 'none';

        el.convList.innerHTML = list.map(function(c) {
            var activeClass = c.id === state.activeConvId ? ' active' : '';
            return '<div class="conv-item' + activeClass + '" data-conv-id="' + escHtml(c.id) + '">' +
                '<div class="conv-title">' + escHtml(c.title) + '</div>' +
                '<div class="conv-meta">' + c.messageCount + ' 条消息</div>' +
                '<button class="btn-delete-conv" data-delete="' + escHtml(c.id) + '" title="删除对话">' +
                '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>' +
                '</button></div>';
        }).join('');

        // Event delegation for conv list
        el.convList.onclick = function(e) {
            var delBtn = e.target.closest('.btn-delete-conv');
            if (delBtn) {
                e.stopPropagation();
                deleteConversation(delBtn.getAttribute('data-delete'));
                return;
            }
            var convItem = e.target.closest('.conv-item');
            if (convItem) {
                switchConversation(convItem.getAttribute('data-conv-id'));
            }
        };
    }

    function renderMessages() {
        var hasMsgs = state.messages.length > 0 || state.isStreaming;

        if (hasMsgs) {
            el.chatMessages.style.display = '';
            el.welcomeScreen.style.display = 'none';
        } else {
            el.chatMessages.style.display = 'none';
            el.welcomeScreen.style.display = '';
        }

        if (!hasMsgs) return;

        var html = state.messages.map(function(m, i) {
            var roleClass = m.role;
            var avatarLeft = roleClass === 'assistant' ? '<div class="message-avatar"><span>🤖</span></div>' : '';
            var avatarRight = roleClass === 'user' ? '<div class="message-avatar"><span>👤</span></div>' : '';
            var bubbleHtml = '';

            if (m.type === 'tool_call') {
                bubbleHtml = '<div class="tool-call-bubble">' +
                    '<div class="tool-call-header">' +
                    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' +
                    '<span>正在查询: ' + escHtml(m.toolName || '') + '</span></div></div>';
            } else if (m.type === 'tool_result') {
                var expanded = m.expanded !== false;
                bubbleHtml = '<div class="tool-result-bubble">' +
                    '<div class="tool-result-header" data-toggle="' + i + '">' +
                    '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>' +
                    '<span>查询完成: ' + escHtml(m.toolName || '') + '</span>' +
                    '<svg class="expand-icon' + (expanded ? ' expanded' : '') + '" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>' +
                    '</div>' +
                    '<div class="tool-result-body" style="display:' + (expanded ? 'block' : 'none') + '">' + escHtml(m.content || '') + '</div>' +
                    '</div>';
            } else {
                bubbleHtml = '<div class="message-text">' + renderMarkdown(m.content || '') + '</div>';
            }

            return '<div class="message-row ' + roleClass + '">' +
                avatarLeft +
                '<div class="message-bubble ' + roleClass + '">' + bubbleHtml + '</div>' +
                avatarRight +
                '</div>';
        }).join('');

        // Typing indicator
        if (state.isStreaming) {
            html += '<div class="message-row assistant">' +
                '<div class="message-avatar"><span>🤖</span></div>' +
                '<div class="message-bubble assistant typing-bubble">' +
                '<span class="dot"></span><span class="dot"></span><span class="dot"></span>' +
                '</div></div>';
        }

        el.messagesWrapper.innerHTML = html;

        // Tool result toggle
        var headers = el.messagesWrapper.querySelectorAll('.tool-result-header');
        headers.forEach(function(hdr) {
            hdr.onclick = function() {
                var idx = parseInt(this.getAttribute('data-toggle'));
                if (state.messages[idx]) {
                    state.messages[idx].expanded = state.messages[idx].expanded === false ? true : false;
                    renderMessages();
                }
            };
        });

        autoScroll();
    }

    function autoScroll() {
        setTimeout(function() {
            if (el.chatMessages) {
                el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
            }
        }, 50);
    }

    // --- API ---
    function fetchJson(url, opts) {
        return fetch(url, opts)
            .then(function(r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .catch(function(e) {
                console.error('API error:', e);
                return { success: false, error: e.message };
            });
    }

    function loadConversations() {
        fetchJson('/api/cs/conversations').then(function(result) {
            if (result.success) {
                state.conversations = result.data || [];
            }
            renderConversations();
        });
    }

    function switchConversation(id) {
        state.activeConvId = id;
        fetchJson('/api/cs/conversation/' + id).then(function(result) {
            if (result.success && result.data) {
                state.messages = (result.data.messages || []).map(function(m) {
                    return {
                        role: m.role,
                        type: m.role === 'tool' ? 'tool_result' : 'text',
                        content: m.content,
                        toolName: m.toolName,
                        expanded: false
                    };
                });
            }
            renderMessages();
            renderConversations();
            autoScroll();
        });
    }

    function deleteConversation(id) {
        fetchJson('/api/cs/conversation/' + id, { method: 'DELETE' }).then(function() {
            if (state.activeConvId === id) {
                state.activeConvId = null;
                state.messages = [];
                renderMessages();
            }
            loadConversations();
        });
    }

    function newChat() {
        state.activeConvId = null;
        state.messages = [];
        state.currentStreamContent = '';
        renderMessages();
        renderConversations();
        el.chatInput.focus();
    }

    // --- Chat / SSE ---
    var streamingMsgIndex = -1;

    function sendMessage(text) {
        var msg = (text || el.chatInput.value).trim();
        if (!msg || state.isStreaming) return;

        el.chatInput.value = '';
        updateSendButton();
        state.isStreaming = true;
        state.currentStreamContent = '';
        streamingMsgIndex = -1;

        // Add user message
        state.messages.push({ role: 'user', type: 'text', content: msg });
        renderMessages();

        var body = JSON.stringify({
            message: msg,
            conversationId: state.activeConvId || null
        });

        fetch('/api/cs/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body
        }).then(function(resp) {
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            return readSseStream(resp);
        }).catch(function(e) {
            state.messages.push({
                role: 'assistant',
                type: 'text',
                content: '抱歉，请求失败: ' + e.message
            });
            finishStreaming();
        });
    }

    function readSseStream(resp) {
        var reader = resp.body.getReader();
        var decoder = new TextDecoder();
        var buffer = '';

        function pump() {
            return reader.read().then(function(result) {
                if (result.done) {
                    // Process remaining buffer
                    if (buffer.trim()) processSseBuffer(buffer);
                    finishStreaming();
                    return;
                }
                buffer += decoder.decode(result.value, { stream: true });
                // Process complete SSE events (separated by double newline)
                var parts = buffer.split('\n\n');
                buffer = parts.pop() || ''; // keep incomplete part
                parts.forEach(processSseBlock);
                return pump();
            });
        }
        return pump();
    }

    function processSseBlock(block) {
        if (!block.trim()) return;
        var lines = block.split('\n');
        var eventName = 'message';
        var eventData = '';

        lines.forEach(function(line) {
            if (line.indexOf('event:') === 0) {
                eventName = line.substring(6).trim();
            } else if (line.indexOf('data:') === 0) {
                eventData = line.substring(5).trim();
            }
        });

        if (!eventData) return;

        try {
            var data = JSON.parse(eventData);
            handleSseEvent(eventName, data);
        } catch (e) {
            // Non-JSON data
            if (eventName === 'text') {
                appendStreamingChunk(eventData);
            }
        }
    }

    function processSseBuffer(buffer) {
        // Process any SSE format data in the buffer
        var lines = buffer.split('\n');
        lines.forEach(function(line) {
            if (line.indexOf('data:') === 0) {
                var data = line.substring(5).trim();
                try {
                    var parsed = JSON.parse(data);
                    if (parsed.content) appendStreamingChunk(parsed.content);
                } catch (e) {
                    appendStreamingChunk(data);
                }
            }
        });
    }

    function handleSseEvent(eventName, data) {
        switch (eventName) {
            case 'text':
                appendStreamingChunk(data.content || '');
                break;
            case 'tool_call':
                state.messages.push({
                    role: 'assistant',
                    type: 'tool_call',
                    toolName: data.tool || '',
                    content: ''
                });
                renderMessages();
                break;
            case 'tool_result':
                state.messages.push({
                    role: 'assistant',
                    type: 'tool_result',
                    toolName: data.tool || '',
                    content: data.result || '',
                    expanded: false
                });
                renderMessages();
                break;
            case 'meta':
                if (data.conversationId && !state.activeConvId) {
                    state.activeConvId = data.conversationId;
                }
                break;
            case 'error':
                state.messages.push({
                    role: 'assistant',
                    type: 'text',
                    content: data.message || '请求出错'
                });
                state.isStreaming = false;
                streamingMsgIndex = -1;
                renderMessages();
                break;
            case 'done':
                // Streaming message finalized by finishStreaming
                break;
        }
        autoScroll();
    }

    function appendStreamingChunk(chunk) {
        if (streamingMsgIndex < 0) {
            // Create new streaming message
            state.messages.push({
                role: 'assistant',
                type: 'text',
                content: '',
                _streaming: true
            });
            streamingMsgIndex = state.messages.length - 1;
        }
        state.messages[streamingMsgIndex].content += chunk;
        state.currentStreamContent += chunk;
        renderMessages();
    }

    function finishStreaming() {
        if (streamingMsgIndex >= 0 && state.messages[streamingMsgIndex]) {
            state.messages[streamingMsgIndex]._streaming = false;
        }
        state.isStreaming = false;
        streamingMsgIndex = -1;
        state.currentStreamContent = '';
        renderMessages();
        autoScroll();
        // Refresh conversation list
        loadConversations();
    }

    // --- Input ---
    function updateSendButton() {
        var hasText = el.chatInput.value.trim().length > 0;
        el.btnSend.disabled = !hasText || state.isStreaming;
    }

    // --- Init ---
    function init() {
        cacheDom();

        el.chatInput.addEventListener('input', updateSendButton);
        el.chatInput.addEventListener('keyup', function(e) {
            if (e.key === 'Enter') sendMessage();
            updateSendButton();
        });

        el.btnSend.addEventListener('click', function() { sendMessage(); });
        document.getElementById('btnNewChat').addEventListener('click', newChat);

        document.getElementById('btnToggleSidebar').addEventListener('click', function() {
            state.sidebarCollapsed = !state.sidebarCollapsed;
            if (state.sidebarCollapsed) {
                el.sidebar.classList.add('collapsed');
            } else {
                el.sidebar.classList.remove('collapsed');
            }
        });

        // Suggestion chips
        var chips = document.querySelectorAll('#suggestions .chip');
        chips.forEach(function(chip) {
            chip.addEventListener('click', function() {
                sendMessage(this.textContent.trim());
            });
        });

        // Load conversations
        loadConversations();
        el.chatInput.focus();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
