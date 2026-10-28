export const retryDelay = 60_000;
export const maxRetries = 10;

export class RetriesExhausted extends Error {
  constructor() {
    super(
      `Server still did not respond after ${maxRetries} retries. Save could not be confirmed. Please try again.`,
    );
  }
}
