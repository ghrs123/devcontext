import { API_BASE_URL, DEFAULT_TIMEOUT_MS } from './config.js';

const RECOMMENDATION_PATH = '/api/widget/v1/size-recommendation';

export class NetworkError extends Error {
	constructor(message, cause) {
		super(message);
		this.name = 'NetworkError';
		this.cause = cause;
	}
}

export class ApiError extends Error {
	constructor(message, code, cause) {
		super(message);
		this.name = 'ApiError';
		this.code = code || 'API_ERROR';
		this.cause = cause;
	}
}

function buildUrl(apiBaseUrl) {
	const base = (apiBaseUrl || API_BASE_URL || '').trim();
	if (!base) {
		return RECOMMENDATION_PATH;
	}
	return `${base.replace(/\/$/, '')}${RECOMMENDATION_PATH}`;
}

async function readJsonSafe(response) {
	try {
		return await response.json();
	} catch {
		return null;
	}
}

export async function getRecommendation(apiKey, payload, apiBaseUrl) {
	const controller = new AbortController();
	const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

	try {
		const response = await fetch(buildUrl(apiBaseUrl), {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
				'X-FitVision-Key': apiKey
			},
			body: JSON.stringify(payload),
			signal: controller.signal
		});

		const body = await readJsonSafe(response);
		const apiSuccess = Boolean(body && body.success === true);

		if (!response.ok || !apiSuccess) {
			const errorCode = body && body.error ? body.error.code : 'API_ERROR';
			const errorMessage = body && body.error && body.error.message
				? body.error.message
				: 'Could not complete the recommendation request.';
			throw new ApiError(errorMessage, errorCode);
		}

		return body.data;
	} catch (error) {
		if (error instanceof ApiError || error instanceof NetworkError) {
			throw error;
		}
		if (error && error.name === 'AbortError') {
			throw new NetworkError('Request timed out. Please try again.', error);
		}
		throw new NetworkError('Network request failed. Please try again.', error);
	} finally {
		clearTimeout(timeoutId);
	}
}
