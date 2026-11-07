package io.github.ukpratik.mukhar.data.billing

import javax.inject.Inject

/** Play flavor: the store's billing is Google Play Billing. */
class StoreBillingRepository @Inject constructor(play: PlayBillingRepository) : BillingRepository by play
