import { useState } from 'react';
import { setApiKey, getApiKey } from '../api/claude';
import { setGeminiKey, getGeminiKey } from '../api/gemini';

interface Props {
  onClose: () => void;
  onSave: () => void;
}

export default function ApiKeyModal({ onClose, onSave }: Props) {
  const [claudeKey, setClaudeKey] = useState(getApiKey() ?? '');
  const [geminiKey, setGeminiKeyState] = useState(getGeminiKey() ?? '');
  const [showClaude, setShowClaude] = useState(false);
  const [showGemini, setShowGemini] = useState(false);

  const handleSave = () => {
    if (claudeKey.trim()) {
      setApiKey(claudeKey.trim());
    }
    if (geminiKey.trim()) {
      setGeminiKey(geminiKey.trim());
    }
    onSave();
  };

  const isClaudeValid = claudeKey.startsWith('sk-ant-');

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
        <h2 className="text-lg font-semibold mb-4">Settings</h2>

        {/* Claude API Key */}
        <div className="mb-6">
          <h3 className="font-medium text-gray-900 mb-1">Claude API Key</h3>
          <p className="text-sm text-gray-500 mb-3">
            Required for recipe generation
          </p>
          <div className="relative">
            <input
              type={showClaude ? 'text' : 'password'}
              value={claudeKey}
              onChange={(e) => setClaudeKey(e.target.value)}
              className="input pr-16"
              placeholder="sk-ant-api03-..."
            />
            <button
              type="button"
              onClick={() => setShowClaude(!showClaude)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-sm text-gray-500 hover:text-gray-700"
            >
              {showClaude ? 'Hide' : 'Show'}
            </button>
          </div>
          {claudeKey && !isClaudeValid && (
            <p className="text-sm text-amber-600 mt-2">
              API keys typically start with "sk-ant-"
            </p>
          )}
        </div>

        {/* Gemini API Key */}
        <div className="mb-6">
          <h3 className="font-medium text-gray-900 mb-1">Gemini API Key</h3>
          <p className="text-sm text-gray-500 mb-3">
            Optional - generates AI images for recipe cards
          </p>
          <div className="relative">
            <input
              type={showGemini ? 'text' : 'password'}
              value={geminiKey}
              onChange={(e) => setGeminiKeyState(e.target.value)}
              className="input pr-16"
              placeholder="API Key from aistudio.google.com"
            />
            <button
              type="button"
              onClick={() => setShowGemini(!showGemini)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-sm text-gray-500 hover:text-gray-700"
            >
              {showGemini ? 'Hide' : 'Show'}
            </button>
          </div>
        </div>

        <p className="text-xs text-gray-500 mb-4">
          Keys are stored locally in your browser and never sent to our servers.
        </p>

        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 btn btn-secondary">
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={!claudeKey.trim()}
            className="flex-1 btn btn-primary disabled:opacity-50"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
}
