function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export async function executeWithRetry(taskFn, retryDecision) {
  let attempt = 0;
  let delay = retryDecision.baseDelayMs;

  while (attempt <= retryDecision.maxRetries) {
    try {
      console.log(`🚀 Attempt ${attempt + 1} started`);

      const result = await taskFn();

      console.log(`✅ Success on attempt ${attempt + 1}`);

      return {
        success: true,
        attempt,
        result,
      };

    } catch (error) {
      attempt++;

      console.log(`❌ Attempt ${attempt} failed: ${error.message}`);

      if (!retryDecision.shouldRetry || attempt > retryDecision.maxRetries) {
        console.log(`🛑 Stopping retries after ${attempt} attempts`);

        return {
          success: false,
          attempt,
          error: error.message,
        };
      }

      console.log(`⏳ Waiting ${delay} ms before next retry...`);

      await sleep(delay);

      if (retryDecision.strategy === "exponential") {
        delay *= 2;
      }
    }
  }
}