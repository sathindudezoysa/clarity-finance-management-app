package com.example.clarity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject

class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getCollection(collectionName: String) = firestore.collection(collectionName)
}
