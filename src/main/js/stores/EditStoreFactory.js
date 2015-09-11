module.exports = function(Backend, chooseIndividAction, requestUpdateAction){
	return Reflux.createStore({

		getInitialState: function(){
			return {};
		},

		init: function(){
			this.listenTo(chooseIndividAction, this.handleIndividChoice);
			this.listenTo(requestUpdateAction, this.handleUpdateRequest);
		},

		handleIndividChoice: function(individUri){
			this.individUri = individUri;
			this.fetchIndividual(individUri);
		},

		fetchIndividual: function(individUri, status){
			var self = this;

			if(self.individUri !== individUri) return;

			Backend.getIndividual(individUri)
				.then(
					function(individualInfo){
						if(self.individUri == individUri){

							self.individualInfo = individualInfo;

							self.trigger({
								individual: individualInfo,
								status: {value: "ok", previous: status}
							});
						}
					},
					function(err){
						if(self.individUri == individUri){
							self.trigger({
								status: _.extend({value: "error", previous: status}, err)
							})
						} else console.log(err);
					}
				);
		},

		handleUpdateRequest: function(updateRequest){
			var self = this;

			var updates = _.map(updateRequest.updates, function(objAndAssertion){
				var subjectAndPredicate = _.pick(updateRequest, "subject", "predicate");
				return _.extend(subjectAndPredicate, objAndAssertion);
			});

			var essentialUpdates = _.filter(updates, function(update){
				var statementObject = update.obj;
				return !_.isNull(statementObject) && !_.isUndefined(statementObject);
			});

			if(_.isEmpty(essentialUpdates))
				this.handleEmptyUpdates(updates);
			else Backend.applyUpdates(essentialUpdates)
				.then(
					_.bind(this.fetchIndividual, this, updateRequest.subject),
					function(err){
						self.fetchIndividual(updateRequest.subject, _.extend({value: "error"}, err));
					}
				);
		},

		handleEmptyUpdates: function(updates){
			if(updates.length === 0) return;
			if(updates.length !== 1) throw new Error("Expecting one empty update");
			var update = updates[0];
			if(this.individUri !== update.subject) return;

			var props = this.individualInfo.owlClass.properties;
			var theProperty = _.find(props, function(prop){
				return (prop.resource.uri === update.predicate);
			});

			if(!theProperty) throw new Error("Unknown property " + update.predicate);
			var valueType = theProperty.type === 'dataProperty' ? 'literal' : 'object';
			var oldValues = this.individualInfo.values;

			var newValues = update.isAssertion
				? oldValues.concat([{
					type: valueType,
					property: theProperty.resource
				}])
				: _.filter(oldValues, function(value){
					return !(value.type === valueType && value.property.uri === update.predicate &&
						(_.isNull(value.value) || _.isUndefined(value.value))
					);
				});

			var newIndividual = _.extend({}, this.individualInfo, {values: newValues});

			this.individualInfo = newIndividual;

			this.trigger({
				individual: newIndividual,
				status: {value: "ok", previous: undefined}
			});
		}

	});
};

