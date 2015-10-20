var Individual = require('../models/Individual.js');

module.exports = function(Backend, chooseIndividAction, requestUpdateAction){
	return Reflux.createStore({

		getInitialState: function(){
			return {
				individual: null,
				status: {value: "ok", previous: null}
			};
		},

		publish: function(state){
			var individ = state.individual;

			this.trigger({
				individual: individ ? new Individual(individ) : individ,
				status: state.status
			});
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
			if(!individUri) {
				this.publish(this.getInitialState());
				return;
			}

			Backend.getIndividual(individUri)
				.then(
					function(individualInfo){
						if(self.individUri == individUri){

							self.individualInfo = individualInfo;

							self.publish({
								individual: individualInfo,
								status: {value: "ok", previous: status}
							});
						}
					},
					function(err){
						if(self.individUri == individUri){
							self.publish({
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
			if(updates.length === 0) {
				this.refresh();
				return;
			}

			if(updates.length !== 1) throw new Error("Expecting one empty update");

			var update = updates[0];
			if(this.individUri !== update.subject) return;

			var props = this.individualInfo.owlClass.properties;
			var theProperty = _.find(props, function(prop){
				return (prop.resource.uri === update.predicate);
			});

			if(!theProperty) throw new Error("Unknown property " + update.predicate);

			if(theProperty.type !== 'dataProperty') {
				this.initiateObjectValueCreation(theProperty.resource.uri);
				return;
			}

			var oldValues = this.individualInfo.values;

			var newValues = update.isAssertion
				? oldValues.concat([{
					type: 'literal',
					property: theProperty.resource
				}])
				: _.reject(oldValues, value =>
						value.property.uri === update.predicate &&
						(_.isNull(value.value) || _.isUndefined(value.value))
				);

			var newIndividual = _.extend({}, this.individualInfo, {values: newValues});

			this.individualInfo = newIndividual;

			this.publish({
				individual: newIndividual,
				status: {value: "ok", previous: undefined}
			});
		},

		initiateObjectValueCreation: function(propertyUri){
			const self = this;
			const individClassUri = this.individualInfo.owlClass.resource.uri;
			const individUri = this.individUri;

			Backend.getRangeValues(individClassUri, propertyUri).then(
				rangeValues => {
					if(individUri !== self.individUri) return;

					let owlClass = self.individualInfo.owlClass;
					let newOwlClassProps = owlClass.properties.map(prop =>
						prop.resource.uri === propertyUri
							? _.extend({}, prop, {rangeValues: _.sortBy(rangeValues, 'displayName')})
							: prop
					);
					let newOwlClass = _.extend({}, owlClass, {properties: newOwlClassProps});
					let newIndividInfo = _.extend({}, self.individualInfo, {owlClass: newOwlClass});

					self.publish({
						individual: newIndividInfo,
						status: {value: "ok", previous: undefined}
					});
				}
			);
		},

		refresh: function(){
			this.publish({
				individual: this.individualInfo,
				status: {value: "ok", previous: undefined}
			});
		}
	});
};

