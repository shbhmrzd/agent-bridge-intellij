#!/usr/bin/env python3
"""Deterministic stdio fixture; never calls a model or reads project files."""
import json
import sys

def emit(value):
    data = json.dumps(value) + '\n'
    # Exercise fragmented reads, including a multibyte UTF-8 character.
    encoded = data.encode('utf-8')
    for offset in range(0, len(encoded), 3):
        sys.stdout.buffer.write(encoded[offset:offset + 3])
        sys.stdout.buffer.flush()

held = None
def validate_handoff(text):
    if not text.endswith('USER_REQUEST\nfixture-handoff-check'):
        return
    history = json.loads(text.split('\nPREVIOUS_CONVERSATION_JSON\n', 1)[1].split('\n\nCURRENT_EDITOR_CONTEXT_JSON\n', 1)[0])
    assert history['turns'] == [{'provider': 'Claude', 'model': 'sonnet', 'user': 'Earlier question', 'assistant': 'Earlier answer 🌍'}]
    context = json.loads(text.split('\nCURRENT_EDITOR_CONTEXT_JSON\n', 1)[1].split('\n\nUSER_REQUEST\n', 1)[0])
    assert context['files'][0]['text'] == 'fresh unsaved buffer'

if '--model' in sys.argv:
    assert sys.argv[sys.argv.index('--model') + 1] == 'fixture-model', 'model flag was not preserved'
if '-p' in sys.argv:
    text = sys.stdin.read()
    validate_handoff(text)
    emit({'type': 'system', 'session_id': 'fixture-session'})
    emit({'type': 'stream_event', 'event': {'delta': {'type': 'text_delta', 'text': 'Claude fixture reply'}}})
    emit({'type': 'result', 'is_error': False, 'session_id': 'fixture-session', 'result': 'Claude fixture reply'})
    sys.exit(0)

for line in sys.stdin:
    request = json.loads(line)
    method = request.get('method')
    if method == 'initialize':
        emit({'id': request['id'], 'result': {'protocolVersion': 1, 'agentCapabilities': {}}})
    elif method == 'thread/start':
        assert request['params'].get('model', 'fixture-model') == 'fixture-model', 'model ID was not forwarded'
        emit({'id': request['id'], 'result': {'thread': {'id': 'thread-fixture'}}})
    elif method == 'model/list':
        assert request['params']['includeHidden'] is False
        page = request['params'].get('cursor')
        assert page in [None, 'page-2']
        model = 'fixture-first' if page is None else 'fixture-second'
        emit({'id': request['id'], 'result': {'data': [{'id': 'catalog-row', 'model': model, 'displayName': model.title()}], 'nextCursor': 'page-2' if page is None else None}})
    elif method == 'session/new':
        emit({'id': request['id'], 'result': {'sessionId': 'session-fixture', 'modes': {'availableModes': [{'id': 'plan'}]}}})
    elif method == 'session/set_mode':
        emit({'id': request['id'], 'result': {}})
    elif method == 'turn/start':
        validate_handoff(request['params']['input'][0]['text'])
        emit({'id': request['id'], 'result': {'turn': {'id': 'turn-fixture'}}})
        emit({'method': 'item/agentMessage/delta', 'params': {'delta': 'Codex fixture reply'}})
        emit({'method': 'turn/completed', 'params': {'turn': {'id': 'turn-fixture', 'status': 'completed'}}})
    elif method == 'session/prompt':
        validate_handoff(request['params']['prompt'][0]['text'])
        emit({'method': 'session/update', 'params': {'sessionId': 'session-fixture', 'update': {'sessionUpdate': 'agent_message_chunk', 'content': {'type': 'text', 'text': 'Copilot fixture reply'}}}})
        emit({'id': request['id'], 'result': {'stopReason': 'end_turn'}})
    elif method == 'echo':
        emit({'id': request['id'], 'result': request['params']})
    elif method == 'hold':
        held = request
    elif method == 'release':
        emit({'id': request['id'], 'result': {'order': 2}})
        if held:
            emit({'id': held['id'], 'result': {'order': 1}})
    elif method == 'permission':
        held = request
        emit({'jsonrpc': '2.0', 'id': 'agent-approval', 'method': 'session/request_permission', 'params': {}})
    elif request.get('id') == 'agent-approval':
        emit({'id': held['id'], 'result': request.get('result', {})})
    elif method == 'fail':
        emit({'id': request['id'], 'error': {'code': -32000, 'message': 'fixture failure'}})
    elif method == 'exit':
        sys.exit(3)
